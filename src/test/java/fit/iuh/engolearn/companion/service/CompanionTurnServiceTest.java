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
import fit.iuh.engolearn.companion.service.impl.CompanionToolRegistry;
import fit.iuh.engolearn.companion.service.CompanionMemoryService;
import fit.iuh.engolearn.companion.service.MemoryRetrievalService;
import fit.iuh.engolearn.companion.service.MemorySettingsSnapshot;
import fit.iuh.engolearn.companion.service.CompanionMemoryToolContext;
import fit.iuh.engolearn.config.CompanionProperties;
import fit.iuh.engolearn.quota.contract.QuotaAvailability;
import fit.iuh.engolearn.quota.contract.QuotaUsageRecording;
import fit.iuh.engolearn.quota.contract.dto.AvailabilityResultDto;
import fit.iuh.engolearn.quota.contract.dto.UsageResultDto;
import fit.iuh.engolearn.shared.messaging.MongoMessageTransactions;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class CompanionTurnServiceTest {
    @Mock CurrentUserProvider users;
    @Mock CompanionConversationRepository conversations;
    @Mock CompanionMessageRepository messages;
    @Mock CompanionModel model;
    @Mock CompanionContextBuilder contextBuilder;
    @Mock MongoMessageTransactions transactions;
    @Mock QuotaAvailability quotaAvailability;
    @Mock QuotaUsageRecording quotaUsage;
    @Mock CompanionToolRegistry toolRegistry;
    @Mock CompanionMemoryService memories;
    @Mock MemoryRetrievalService memoryRetrieval;
    @Mock fit.iuh.engolearn.companion.service.impl.ScreenContextResolverRegistry screenContexts;
    ExecutorService toolExecutor;
    CompanionProperties properties;
    CompanionTurnServiceImpl service;

    @BeforeEach void setUp() {
        properties = new CompanionProperties();
        toolExecutor = Executors.newFixedThreadPool(2);
        when(users.requireCurrentUserId()).thenReturn("learner-1");
        when(transactions.required(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        when(quotaAvailability.checkAvailability(anyString(), anyString()))
                .thenReturn(new AvailabilityResultDto("AI_COMPANION_TOKEN_USAGE", 0, 100, 100, "TOKEN",
                        "DAILY", Instant.now(), Instant.now().plusSeconds(86400), Instant.now(), "AVAILABLE"));
        service = new CompanionTurnServiceImpl(users, conversations, messages, model, contextBuilder, properties,
                transactions, quotaAvailability, quotaUsage, toolRegistry, memories, memoryRetrieval, screenContexts, toolExecutor);
    }

    @AfterEach void closeToolExecutor() { toolExecutor.shutdownNow(); }

    private void stubUsageRecording() {
        when(quotaUsage.recordUsage(any())).thenAnswer(invocation -> {
            var command = invocation.<fit.iuh.engolearn.quota.contract.dto.UsageCommandDto>getArgument(0);
            return new UsageResultDto(true, "ledger-1", command.amount(), Instant.now(), "allowance-1", null);
        });
    }

    private void stubPrompt() {
        when(contextBuilder.build(anyString(), anyList(), anyString(), any(), anyBoolean()))
                .thenReturn(java.util.List.of(new CompanionPromptMessage(CompanionPromptMessage.Role.SYSTEM, "prompt"),
                        new CompanionPromptMessage(CompanionPromptMessage.Role.USER, "question")));
    }

    private void stubMemorySettings() {
        when(memories.settingsSnapshot(anyString())).thenReturn(new MemorySettingsSnapshot(true, 0));
    }

    @Test void failedProviderAttemptCanRetryWithoutInsertingAnotherUserMessage() {
        stubUsageRecording();
        stubPrompt();
        stubMemorySettings();
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
                completedText("Hello", 3));
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
        verify(quotaUsage).recordUsage(argThat(command -> command.amount() == 3
                && command.idempotencyKey().equals("turn:" + conversationId + ":" + turnId)));
        verify(conversations).admitNew(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100));
        verify(messages, times(2)).insert(any());
        verify(conversations).claimRetry(eq(conversationId), eq("learner-1"), eq(turnId), any(), any(), eq(100));
    }

    @Test void learnerCannotHoldTurnsAcrossTwoConversationsAtOnce() {
        stubMemorySettings();
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
        stubMemorySettings();
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
        when(model.stream(any())).thenReturn(Flux.<CompanionModelEvent>never().doOnSubscribe(subscription -> {
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

    @Test void assessmentToolRoundTripsTypedResultsAndAggregatesUsageBeforeAtomicCompletion() {
        stubUsageRecording();
        stubPrompt();
        stubMemorySettings();
        String conversationId = "conversation-tools";
        String turnId = UUID.randomUUID().toString();
        when(conversations.findOwned(conversationId, "learner-1")).thenReturn(Optional.of(conversation(conversationId, 0, 1, null, null)),
                Optional.of(conversation(conversationId, 2, 3, null, null)));
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER)).thenReturn(Optional.empty());
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT)).thenReturn(Optional.empty());
        when(conversations.admitNew(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100)))
                .thenReturn(Optional.of(conversation(conversationId, 1, 2, turnId, 1L)));
        when(messages.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var continuation = new CompanionModelContinuation() {};
        var call = new CompanionToolCall("call-1", "getLatestAssessmentResult", java.util.Map.of("detail", "SUMMARY"));
        when(model.stream(any())).thenReturn(Flux.just(new CompanionModelEvent.ToolCalls(continuation, java.util.List.of(call)),
                        new CompanionModelEvent.RoundCompleted(new CompanionUsage(11))),
                completedText("Grounded advice", 7));
        when(conversations.finish(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), eq(2L), any())).thenReturn(true);
        when(messages.insert(any())).thenAnswer(invocation -> {
            CompanionMessage value = invocation.getArgument(0);
            value.setId("message-" + value.getSequence());
            return value;
        });
        when(toolRegistry.execute(eq("getLatestAssessmentResult"), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(3).run();
                    return "{\"status\":\"SUCCESS\"}";
                });

        var prepared = service.prepareTurn(conversationId, new CompanionTurnRequest(UUID.fromString(turnId), "How did I do?", false));
        List<CompanionTurnService.TurnStatus> statuses = new ArrayList<>();
        var result = service.streamPreparedTurn(prepared, observer(statuses));

        assertEquals("Grounded advice", result.assistantMessage().content());
        assertEquals(List.of(CompanionTurnService.TurnStatus.ASSESSMENT_LOOKUP), statuses);
        var requests = org.mockito.ArgumentCaptor.forClass(CompanionModelRequest.class);
        verify(model, times(2)).stream(requests.capture());
        assertEquals(continuation, requests.getAllValues().get(1).continuation());
        assertEquals(java.util.List.of(new CompanionToolResult("call-1", "getLatestAssessmentResult", "{\"status\":\"SUCCESS\"}")),
                requests.getAllValues().get(1).toolResults());
        verify(quotaUsage).recordUsage(argThat(command -> command.amount() == 18));
    }

    @Test void assessmentStatusObserverFailureReleasesTurnWithoutPersistingOrCharging() {
        stubPrompt();
        stubMemorySettings();
        String conversationId = "conversation-observer-failure";
        String turnId = UUID.randomUUID().toString();
        when(conversations.findOwned(conversationId, "learner-1"))
                .thenReturn(Optional.of(conversation(conversationId, 0, 1, null, null)));
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER)).thenReturn(Optional.empty());
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT)).thenReturn(Optional.empty());
        when(conversations.admitNew(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100)))
                .thenReturn(Optional.of(conversation(conversationId, 1, 2, turnId, 1L)));
        when(messages.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(model.stream(any())).thenReturn(Flux.just(
                new CompanionModelEvent.ToolCalls(new CompanionModelContinuation() {}, List.of(
                        new CompanionToolCall("call-1", "getLatestAssessmentResult", java.util.Map.of()))),
                new CompanionModelEvent.RoundCompleted(new CompanionUsage(4))));
        when(toolRegistry.execute(eq("getLatestAssessmentResult"), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class)))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(3).run();
                    return "{\"status\":\"SUCCESS\"}";
                });
        when(conversations.release(eq(conversationId), eq("learner-1"), eq(turnId), anyLong(), any())).thenReturn(true);
        var prepared = service.prepareTurn(conversationId,
                new CompanionTurnRequest(UUID.fromString(turnId), "How did I do?", false));
        CompanionTurnService.TurnObserver observer = new CompanionTurnService.TurnObserver() {
            @Override public void onDelta(String text) {}
            @Override public boolean isCancelled() { return false; }
            @Override public void onStatus(CompanionTurnService.TurnStatus status) {
                throw new IllegalStateException("stream is closed");
            }
        };

        assertThrows(CompanionException.class, () -> service.streamPreparedTurn(prepared, observer));

        verify(conversations).release(eq(conversationId), eq("learner-1"), eq(turnId), anyLong(), any());
        verify(conversations, never()).finish(anyString(), anyString(), anyString(), anyLong(), anyLong(), any());
        verify(quotaUsage, never()).recordUsage(any());
    }

    @Test void assessmentCallSkippedByToolBudgetDoesNotEmitStatus() {
        stubUsageRecording();
        stubPrompt();
        stubMemorySettings();
        properties.setMaxToolCalls(1);
        String conversationId = "conversation-budget";
        String turnId = UUID.randomUUID().toString();
        when(conversations.findOwned(conversationId, "learner-1"))
                .thenReturn(Optional.of(conversation(conversationId, 0, 1, null, null)),
                        Optional.of(conversation(conversationId, 2, 3, null, null)));
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.USER)).thenReturn(Optional.empty());
        when(messages.findByTurnAndRole(conversationId, turnId, CompanionMessage.Role.ASSISTANT)).thenReturn(Optional.empty());
        when(conversations.admitNew(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100)))
                .thenReturn(Optional.of(conversation(conversationId, 1, 2, turnId, 1L)));
        when(messages.insert(any())).thenAnswer(invocation -> {
            CompanionMessage value = invocation.getArgument(0);
            value.setId("message-" + value.getSequence());
            return value;
        });
        when(conversations.finish(eq(conversationId), eq("learner-1"), eq(turnId), eq(1L), eq(2L), any())).thenReturn(true);
        var calls = List.of(new CompanionToolCall("call-1", "otherTool", java.util.Map.of()),
                new CompanionToolCall("call-2", "getLatestAssessmentResult", java.util.Map.of()));
        when(model.stream(any())).thenReturn(Flux.just(new CompanionModelEvent.ToolCalls(new CompanionModelContinuation() {}, calls),
                        new CompanionModelEvent.RoundCompleted(new CompanionUsage(4))),
                completedText("Answer", 3));
        when(toolRegistry.execute(eq("otherTool"), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class)))
                .thenReturn("{\"status\":\"ERROR\"}");
        var prepared = service.prepareTurn(conversationId,
                new CompanionTurnRequest(UUID.fromString(turnId), "How did I do?", false));
        List<CompanionTurnService.TurnStatus> statuses = new ArrayList<>();

        var result = service.streamPreparedTurn(prepared, observer(statuses));

        assertEquals("Answer", result.assistantMessage().content());
        assertTrue(statuses.isEmpty());
        verify(toolRegistry).execute(eq("otherTool"), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class));
        verify(toolRegistry, never()).execute(eq("getLatestAssessmentResult"), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class));
    }

    @Test void missingAuthoritativeUsageFailsWithoutAssistantPersistenceOrCharge() {
        stubPrompt();
        stubMemorySettings();
        String turnId = UUID.randomUUID().toString();
        when(conversations.findOwned("conversation-1", "learner-1"))
                .thenReturn(Optional.of(conversation("conversation-1", 0, 1, null, null)));
        when(messages.findByTurnAndRole(eq("conversation-1"), eq(turnId), any())).thenReturn(Optional.empty());
        when(conversations.admitNew(eq("conversation-1"), eq("learner-1"), eq(turnId), eq(1L), any(), any(), eq(100)))
                .thenReturn(Optional.of(conversation("conversation-1", 1, 2, turnId, 1L)));
        when(messages.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(model.stream(any())).thenReturn(Flux.just(new CompanionModelEvent.TextDelta("Partial")));
        when(conversations.release(eq("conversation-1"), eq("learner-1"), eq(turnId), anyLong(), any())).thenReturn(true);

        var prepared = service.prepareTurn("conversation-1", new CompanionTurnRequest(UUID.fromString(turnId), "Hi", false));
        var failure = assertThrows(CompanionException.class, () -> service.streamPreparedTurn(prepared, observer()));

        assertEquals("COMPANION_USAGE_UNAVAILABLE", failure.getCode());
        verify(messages, times(1)).insert(any());
        verify(conversations, never()).finish(anyString(), anyString(), anyString(), anyLong(), anyLong(), any());
        verify(quotaUsage, never()).recordUsage(any());
    }

    @Test void ownershipIsCheckedBeforeQuotaAndDepletedAllowanceRejectsAdmission() {
        String turnId = UUID.randomUUID().toString();
        when(conversations.findOwned("missing", "learner-1")).thenReturn(Optional.empty());
        when(quotaAvailability.checkAvailability(anyString(), anyString())).thenReturn(
                new AvailabilityResultDto("AI_COMPANION_TOKEN_USAGE", 1, 1, 0, "TOKEN", "DAILY",
                        Instant.now(), Instant.now().plusSeconds(86400), Instant.now(), "EXHAUSTED"));

        var missing = assertThrows(CompanionException.class, () -> service.prepareTurn("missing",
                new CompanionTurnRequest(UUID.fromString(turnId), "Hi", false)));
        assertEquals(404, missing.getStatus());
        verify(quotaAvailability, never()).checkAvailability(anyString(), anyString());

        when(conversations.findOwned("owned", "learner-1"))
                .thenReturn(Optional.of(conversation("owned", 0, 1, null, null)));
        when(messages.findByTurnAndRole(eq("owned"), eq(turnId), any())).thenReturn(Optional.empty());
        var exhausted = assertThrows(CompanionException.class, () -> service.prepareTurn("owned",
                new CompanionTurnRequest(UUID.fromString(turnId), "Hi", false)));
        assertEquals(429, exhausted.getStatus());
        assertEquals("COMPANION_QUOTA_EXHAUSTED", exhausted.getCode());
        verify(conversations, never()).admitNew(eq("owned"), anyString(), anyString(), anyLong(), any(), any(), anyInt());
    }

    private Flux<CompanionModelEvent> completedText(String text, long totalTokens) {
        return Flux.just(new CompanionModelEvent.TextDelta(text),
                new CompanionModelEvent.RoundCompleted(new CompanionUsage(totalTokens)));
    }

    private CompanionTurnService.TurnObserver observer() {
        return new CompanionTurnService.TurnObserver() {
            @Override public void onDelta(String text) {}
            @Override public boolean isCancelled() { return false; }
        };
    }

    private CompanionTurnService.TurnObserver observer(List<CompanionTurnService.TurnStatus> statuses) {
        return new CompanionTurnService.TurnObserver() {
            @Override public void onDelta(String text) {}
            @Override public boolean isCancelled() { return false; }
            @Override public void onStatus(CompanionTurnService.TurnStatus status) { statuses.add(status); }
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
