package fit.iuh.engolearn.companion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fit.iuh.engolearn.companion.dto.request.CompanionTurnRequest;
import fit.iuh.engolearn.companion.exception.CompanionException;
import fit.iuh.engolearn.companion.model.CompanionConversation;
import fit.iuh.engolearn.companion.model.CompanionMessage;
import fit.iuh.engolearn.companion.repository.CompanionConversationRepository;
import fit.iuh.engolearn.companion.repository.CompanionMessageRepository;
import fit.iuh.engolearn.companion.service.impl.CompanionContextBuilder;
import fit.iuh.engolearn.companion.service.impl.CompanionTurnServiceImpl;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class CompanionTurnServiceTest {
    @Mock CurrentUserProvider users;
    @Mock CompanionConversationRepository conversations;
    @Mock CompanionMessageRepository messages;
    @Mock CompanionModel model;
    @Mock CompanionContextBuilder contextBuilder;
    @Mock MongoMessageTransactions transactions;
    CompanionProperties properties;
    CompanionTurnServiceImpl service;

    @BeforeEach void setUp() {
        properties = new CompanionProperties();
        when(users.requireCurrentUserId()).thenReturn("learner-1");
        when(transactions.required(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        service = new CompanionTurnServiceImpl(users, conversations, messages, model, contextBuilder, properties, transactions);
    }

    private void stubPrompt() {
        when(contextBuilder.build(anyString(), anyList(), anyString()))
                .thenReturn(new Prompt(java.util.List.of(new UserMessage("prompt"))));
    }

    @Test void failedProviderAttemptCanRetryWithoutInsertingAnotherUserMessage() {
        stubPrompt();
        String conversationId = "conversation-1";
        String turnId = UUID.randomUUID().toString();
        CompanionConversation conversation = conversation(conversationId, 0, 1, null, null);
        when(conversations.findOwned(conversationId, "learner-1")).thenReturn(Optional.of(conversation),
                Optional.of(conversation(conversationId, 1, 2, turnId, 1L)),
                Optional.of(conversation(conversationId, 2, 3, null, null)));
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER)).thenReturn(Optional.empty());
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT)).thenReturn(Optional.empty());
        when(conversations.admitNew(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100)))
                .thenReturn(Optional.of(conversation(conversationId, 1, 2, turnId, 1L)));
        when(messages.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(model.stream(any())).thenReturn(Flux.error(new CompanionException(503, "COMPANION_PROVIDER_UNAVAILABLE", "Unavailable", false)),
                Flux.just("Hello"));
        when(conversations.claimRetry(eq(conversationId), eq("learner-1"), eq(turnId), any(), any(), eq(100)))
                .thenReturn(Optional.of(CompanionConversation.builder().id(conversationId).learnerId("learner-1")
                        .messageCount(1).nextSequence(2).activeTurnGeneration(2).pendingTurnId(turnId)
                        .pendingUserSequence(1L).createdAt(Instant.now()).updatedAt(Instant.now()).build()));
        CompanionMessage userMessage = message(conversationId, turnId, 1, CompanionMessage.Role.USER, "Hi");
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER)).thenReturn(Optional.empty(), Optional.of(userMessage));
        when(conversations.finish(eq(conversationId), eq("learner-1"), eq(turnId), eq(2L), eq(2L), any())).thenReturn(true);
        when(messages.insert(any())).thenAnswer(invocation -> {
            CompanionMessage value = invocation.getArgument(0);
            value.setId("message-" + value.getSequence());
            return value;
        });

        var prepared = service.prepareTurn(conversationId, new CompanionTurnRequest(UUID.fromString(turnId), "Hi", false));
        assertThrows(CompanionException.class, () -> service.streamPreparedTurn(prepared, observer()));
        var retry = service.prepareTurn(conversationId, new CompanionTurnRequest(UUID.fromString(turnId), "Hi", true));
        var result = service.streamPreparedTurn(retry, observer());

        assertEquals("Hello", result.assistantMessage().content());
        verify(conversations).admitNew(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100));
        verify(messages, times(2)).insert(any());
        verify(conversations).claimRetry(eq(conversationId), eq("learner-1"), eq(turnId), any(), any(), eq(100));
    }

    @Test void learnerCannotHoldTurnsAcrossTwoConversationsAtOnce() {
        String first = UUID.randomUUID().toString();
        when(conversations.findOwned("conversation-1", "learner-1"))
                .thenReturn(Optional.of(conversation("conversation-1", 0, 1, null, null)));
        when(messages.findByTurnAndRole(eq("conversation-1"), eq(first), any())).thenReturn(Optional.empty());
        when(conversations.admitNew(eq("conversation-1"), eq("learner-1"), eq(first), eq(1L), any(), any(), eq(100)))
                .thenReturn(Optional.of(conversation("conversation-1", 1, 2, first, 1L)));
        when(messages.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var prepared = service.prepareTurn("conversation-1", new CompanionTurnRequest(UUID.fromString(first), "one", false));
        assertThrows(CompanionException.class, () -> service.prepareTurn("conversation-2",
                new CompanionTurnRequest(UUID.randomUUID(), "two", false)));
        service.cancelPreparedTurn(prepared);
    }

    @Test void clientCancellationCancelsProviderAndReleasesTheReservation() {
        stubPrompt();
        String turnId = UUID.randomUUID().toString();
        when(conversations.findOwned("conversation-1", "learner-1"))
                .thenReturn(Optional.of(conversation("conversation-1", 0, 1, null, null)));
        when(messages.findByTurnAndRole(eq("conversation-1"), eq(turnId), any())).thenReturn(Optional.empty());
        when(conversations.admitNew(eq("conversation-1"), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100)))
                .thenReturn(Optional.of(conversation("conversation-1", 1, 2, turnId, 1L)));
        when(messages.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicInteger providerCancelled = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Runnable> cancelAction = new AtomicReference<>();
        when(model.stream(any())).thenReturn(Flux.<String>never().doOnSubscribe(subscription -> {
            cancelled.set(true);
            cancelAction.get().run();
        }).doOnCancel(providerCancelled::incrementAndGet));
        when(conversations.release(eq("conversation-1"), eq("learner-1"), eq(turnId), anyLong(), any())).thenReturn(true);
        var prepared = service.prepareTurn("conversation-1", new CompanionTurnRequest(UUID.fromString(turnId), "Hi", false));
        CompanionTurnService.TurnObserver observer = new CompanionTurnService.TurnObserver() {
            @Override public void onDelta(String text) {}
            @Override public boolean isCancelled() { return cancelled.get(); }
            @Override public void setCancelAction(Runnable action) {
                cancelAction.set(action);
            }
        };

        assertThrows(CompanionException.class, () -> service.streamPreparedTurn(prepared, observer));
        assertEquals(1, providerCancelled.get());
        verify(conversations).release(eq("conversation-1"), eq("learner-1"), eq(turnId), anyLong(), any());
    }

    private CompanionTurnService.TurnObserver observer() {
        return new CompanionTurnService.TurnObserver() {
            @Override public void onDelta(String text) {}
            @Override public boolean isCancelled() { return false; }
        };
    }

    private CompanionConversation conversation(String id, int count, long nextSequence, String pending, Long pendingSequence) {
        return CompanionConversation.builder().id(id).learnerId("learner-1").messageCount(count)
                .nextSequence(nextSequence).activeTurnGeneration(1).pendingTurnId(pending)
                .pendingUserSequence(pendingSequence).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private CompanionMessage message(String conversationId, String turnId, long sequence, CompanionMessage.Role role, String content) {
        return CompanionMessage.builder().id("message-" + sequence).conversationId(conversationId).turnId(turnId)
                .sequence(sequence).role(role).content(content).createdAt(Instant.now()).build();
    }
}
