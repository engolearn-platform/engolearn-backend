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
                any(CompanionMemoryToolContext.class), any(CompanionBusinessTurnContext.class)))
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
                any(CompanionMemoryToolContext.class), any(CompanionBusinessTurnContext.class)))
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
                any(CompanionMemoryToolContext.class), any(CompanionBusinessTurnContext.class)))
                .thenReturn("{\"status\":\"ERROR\"}");
        var prepared = service.prepareTurn(conversationId,
                new CompanionTurnRequest(UUID.fromString(turnId), "How did I do?", false));
        List<CompanionTurnService.TurnStatus> statuses = new ArrayList<>();

        var result = service.streamPreparedTurn(prepared, observer(statuses));

        assertEquals("Answer", result.assistantMessage().content());
        assertTrue(statuses.isEmpty());
        verify(toolRegistry).execute(eq("otherTool"), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class), any(CompanionBusinessTurnContext.class));
        verify(toolRegistry, never()).execute(eq("getLatestAssessmentResult"), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class), any(CompanionBusinessTurnContext.class));
    }


    @Test void multiDomainRoundsKeepOneTrustedTimeOrderedResultsAndPartialFailure() {
        stubUsageRecording();
        stubPrompt();
        stubMemorySettings();
        String conversationId = "conversation-multi-domain";
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
        var first = new CompanionModelContinuation() {};
        var second = new CompanionModelContinuation() {};
        var a = new CompanionToolCall("topic-1", "findLearningTopics", java.util.Map.of());
        var b = new CompanionToolCall("path-1", "getLearningPathProgress", java.util.Map.of("mode","ACTIVE"));
        var c = new CompanionToolCall("path-2", "getLearningPathProgress", java.util.Map.of("mode","ACTIVE","cursor","next"));
        var d = new CompanionToolCall("history-1", "getAssessmentHistory", java.util.Map.of());
        when(model.stream(any())).thenReturn(
                Flux.just(new CompanionModelEvent.ToolCalls(first,List.of(a,b)),new CompanionModelEvent.RoundCompleted(new CompanionUsage(4))),
                Flux.just(new CompanionModelEvent.ToolCalls(second,List.of(c,d)),new CompanionModelEvent.RoundCompleted(new CompanionUsage(5))),
                completedText("Partial grounded answer",3));
        var scopes = new java.util.concurrent.CopyOnWriteArrayList<CompanionBusinessTurnContext>();
        var names = new java.util.concurrent.CopyOnWriteArrayList<String>();
        when(toolRegistry.execute(anyString(), anyMap(), eq("learner-1"), any(Runnable.class),
                any(CompanionMemoryToolContext.class), any(CompanionBusinessTurnContext.class))).thenAnswer(invocation -> {
            scopes.add(invocation.getArgument(5));
            String name = invocation.getArgument(0);
            names.add(name);
            return "getAssessmentHistory".equals(name) ? "{\"status\":\"ERROR\"}" : "{\"status\":\"SUCCESS\"}";
        });
        var prepared = service.prepareTurn(conversationId,
                new CompanionTurnRequest(UUID.fromString(turnId), "Guide me", false));
        var statuses = new ArrayList<CompanionTurnService.TurnStatus>();
        assertEquals("Partial grounded answer",service.streamPreparedTurn(prepared,observer(statuses)).assistantMessage().content());
        assertEquals(List.of(a.name(),b.name(),c.name(),d.name()),names);
        assertEquals(4,scopes.size());
        scopes.forEach(scope -> assertSame(scopes.getFirst(),scope));
        assertTrue(statuses.isEmpty());
        var requests = org.mockito.ArgumentCaptor.forClass(CompanionModelRequest.class);
        verify(model,times(3)).stream(requests.capture());
        var sent = requests.getAllValues();
        assertSame(first,sent.get(1).continuation());
        assertSame(second,sent.get(2).continuation());
        assertEquals(List.of("topic-1","path-1"),sent.get(1).toolResults().stream().map(CompanionToolResult::callId).toList());
        assertEquals(List.of("path-2","history-1"),sent.get(2).toolResults().stream().map(CompanionToolResult::callId).toList());
        assertTrue(sent.get(2).toolResults().getLast().json().contains("ERROR"));
        verify(quotaUsage).recordUsage(argThat(command -> command.amount() == 12));
    }

    @org.junit.jupiter.api.RepeatedTest(2)
    void timedOutPathCannotIssueLateCursorAndMixedCallsRemainBounded() {
        stubUsageRecording();
        stubPrompt();
        stubMemorySettings();
        properties.setToolTimeout(java.time.Duration.ofMillis(500));
        properties.setMaxToolCalls(3);
        String id = "conversation-timeout";
        String turnId = UUID.randomUUID().toString();
        var prepared = prepareBusinessTurn(id,turnId,true);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var allowCompletion = new java.util.concurrent.CountDownLatch(1);
        var workerReturned = new java.util.concurrent.CountDownLatch(1);
        var paths = mock(fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads.class);
        var real = pathRegistry(paths);
        var scope = new AtomicReference<CompanionBusinessTurnContext>();
        when(paths.active(eq("learner-1"),any(Instant.class),any())).thenAnswer(invocation -> {
            entered.countDown();
            awaitIgnoringInterrupt(allowCompletion);
            return latePathPage(invocation.getArgument(1));
        });
        when(toolRegistry.execute(anyString(),anyMap(),eq("learner-1"),any(Runnable.class),
                any(CompanionMemoryToolContext.class),any(CompanionBusinessTurnContext.class))).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            if (!"getLearningPathProgress".equals(name)) return "{\"status\":\"SUCCESS\"}";
            scope.set(invocation.getArgument(5));
            var args = invocation.<java.util.Map<String,Object>>getArgument(1);
            try {
                return real.execute(name,args,"learner-1",()->{},null,scope.get());
            } finally {
                if (!args.containsKey("cursor")) workerReturned.countDown();
            }
        });
        doAnswer(invocation -> {
            real.acceptResult(invocation.getArgument(0),invocation.getArgument(1),invocation.getArgument(2),
                    invocation.getArgument(3),invocation.getArgument(4));
            return null;
        }).when(toolRegistry).acceptResult(anyString(),anyMap(),eq("learner-1"),any(),anyString());
        var first = new CompanionToolCall("path-1","getLearningPathProgress",java.util.Map.of("mode","ACTIVE"));
        var forged = new CompanionToolCall("path-2","getLearningPathProgress",java.util.Map.of("mode","ACTIVE","cursor","late-cursor"));
        var topic = new CompanionToolCall("topic","findLearningTopics",java.util.Map.of());
        var skipped = new CompanionToolCall("history","getAssessmentHistory",java.util.Map.of());
        var requests = new ArrayList<CompanionModelRequest>();
        when(model.stream(any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            if (requests.size() == 1) return businessRound(List.of(first),4);
            if (requests.size() == 2) {
                assertEquals(0,entered.getCount());
                assertTrue(requests.getLast().toolResults().getFirst().json().contains("took too long"));
                allowCompletion.countDown();
                awaitLatch(workerReturned);
                assertFalse(scope.get().canContinue("learner-1","ACTIVE","late-cursor"));
                return businessRound(List.of(forged,topic,skipped),5);
            }
            return completedText("Partial answer",3);
        });
        try {
            assertEquals("Partial answer",service.streamPreparedTurn(prepared,observer()).assistantMessage().content());
            assertEquals(3,requests.size());
            var results = requests.getLast().toolResults();
            assertEquals(List.of("path-2","topic","history"),results.stream().map(CompanionToolResult::callId).toList());
            assertTrue(results.get(0).json().contains("INVALID_ARGUMENTS"));
            assertTrue(results.get(1).json().contains("SUCCESS"));
            assertTrue(results.get(2).json().contains("budget"));
            verify(paths,times(1)).active(eq("learner-1"),any(Instant.class),any());
            verify(toolRegistry,never()).execute(eq("getAssessmentHistory"),anyMap(),anyString(),any(),any(),any());
            verify(quotaUsage).recordUsage(argThat(command -> command.amount() == 12));
        } finally {
            allowCompletion.countDown();
        }
    }

    @org.junit.jupiter.api.RepeatedTest(2)
    void cancellationDuringPathReadDoesNotPublishOrInvokeLaterCalls() throws Exception {
        stubPrompt();
        stubMemorySettings();
        String id = "conversation-tool-cancel";
        String turnId = UUID.randomUUID().toString();
        var prepared = prepareBusinessTurn(id,turnId,false);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var allowCompletion = new java.util.concurrent.CountDownLatch(1);
        var workerReturned = new java.util.concurrent.CountDownLatch(1);
        var paths = mock(fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads.class);
        var real = pathRegistry(paths);
        var scope = new AtomicReference<CompanionBusinessTurnContext>();
        when(paths.active(eq("learner-1"),any(Instant.class),any())).thenAnswer(invocation -> {
            entered.countDown();
            awaitIgnoringInterrupt(allowCompletion);
            return latePathPage(invocation.getArgument(1));
        });
        when(toolRegistry.execute(eq("getLearningPathProgress"),anyMap(),eq("learner-1"),any(Runnable.class),
                any(CompanionMemoryToolContext.class),any(CompanionBusinessTurnContext.class))).thenAnswer(invocation -> {
            scope.set(invocation.getArgument(5));
            try {
                return real.execute(invocation.getArgument(0),invocation.getArgument(1),"learner-1",()->{},null,scope.get());
            } finally {
                workerReturned.countDown();
            }
        });
        when(model.stream(any())).thenReturn(businessRound(List.of(
                new CompanionToolCall("path","getLearningPathProgress",java.util.Map.of("mode","ACTIVE")),
                new CompanionToolCall("topic","findLearningTopics",java.util.Map.of())),4));
        var cancelled = new AtomicBoolean();
        var turnExecutor = Executors.newSingleThreadExecutor();
        try {
            var future = turnExecutor.submit(() -> service.streamPreparedTurn(prepared,new CompanionTurnService.TurnObserver() {
                @Override public void onDelta(String text) {}
                @Override public boolean isCancelled() { return cancelled.get(); }
            }));
            awaitLatch(entered);
            cancelled.set(true);
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> future.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(CompanionException.class,failure.getCause());
            assertEquals("TURN_CANCELLED",((CompanionException)failure.getCause()).getCode());
            allowCompletion.countDown();
            awaitLatch(workerReturned);
            assertFalse(scope.get().canContinue("learner-1","ACTIVE","late-cursor"));
            verify(model,times(1)).stream(any());
            verify(toolRegistry,never()).acceptResult(anyString(),anyMap(),anyString(),any(),anyString());
            verify(toolRegistry,never()).execute(eq("findLearningTopics"),anyMap(),anyString(),any(),any(),any());
            verify(conversations).release(eq(id),eq("learner-1"),eq(turnId),anyLong(),any());
            verify(conversations,never()).finish(anyString(),anyString(),anyString(),anyLong(),anyLong(),any());
            verify(quotaUsage,never()).recordUsage(any());
        } finally {
            allowCompletion.countDown();
            turnExecutor.shutdownNow();
        }
    }

    @Test void safeTraceClassifiesOnlyTopLevelStatusAndNeverLogsCanaries() {
        stubUsageRecording();
        stubPrompt();
        stubMemorySettings();
        var prepared = prepareBusinessTurn("conversation-trace",UUID.randomUUID().toString(),true);
        var hostile = "name-CANARY\r\nFAKE";
        properties.setMaxToolCalls(6);
        var names = List.of("findLearningTopics","getLearningPathProgress","getAssessmentHistory",hostile,
                "getTopicLearningProgress","getTopicLearningProgress");
        var calls = new ArrayList<CompanionToolCall>();
        for (int i=0;i<names.size();i++)
            calls.add(new CompanionToolCall("id-CANARY-"+i,names.get(i),java.util.Map.of("cursor","arg-CANARY","owner","owner-CANARY")));
        when(model.stream(any())).thenReturn(businessRound(calls,5),completedText("Done",3));
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var realRegistry = new CompanionToolRegistry(List.of(
                new fit.iuh.engolearn.companion.service.impl.CompanionTopicToolHandler(
                        mock(fit.iuh.engolearn.topic.contract.TopicCompanionReads.class),mapper),
                new fit.iuh.engolearn.companion.service.impl.CompanionLearningPathToolHandler(
                        mock(fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads.class),mapper),
                new fit.iuh.engolearn.companion.service.impl.CompanionAssessmentHistoryToolHandler(
                        mock(fit.iuh.engolearn.assessment.contract.AssessmentCompanionResults.class),mapper)));
        when(toolRegistry.safeName(anyString())).thenAnswer(invocation -> realRegistry.safeName(invocation.getArgument(0)));
        when(toolRegistry.execute(anyString(),anyMap(),eq("learner-1"),any(Runnable.class),any(),any()))
                .thenReturn("{\"data\":{\"status\":\"SUCCESS\",\"secret\":\"result-CANARY\"}}",
                        "{\"status\":\"ERROR\",\"data\":{\"status\":\"SUCCESS\"},\"message\":\"error-CANARY\"}",
                        "{\"status\":\"status-CANARY\"}","invalid-CANARY",
                        "{\"status\":\"SUCCESS\",\"cursor\":\"cursor-CANARY\"}")
                .thenThrow(new IllegalStateException("exception-CANARY"));
        var logger = (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(CompanionTurnServiceImpl.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.streamPreparedTurn(prepared,observer());
            var logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
            var results = logs.stream().filter(line -> line.contains("decision=result")).toList();
            assertEquals(5,results.size());
            assertTrue(results.get(0).contains("code=UNKNOWN_RESULT"));
            assertTrue(results.get(1).contains("code=ERROR"));
            assertTrue(results.get(2).contains("code=UNKNOWN_RESULT"));
            assertTrue(results.get(3).contains("tool=UNKNOWN_TOOL") && results.get(3).contains("code=MALFORMED_RESULT"));
            assertTrue(results.get(4).contains("code=SUCCESS"));
            assertTrue(logs.stream().anyMatch(line -> line.contains("code=TOOL_EXECUTION_FAILED")));
            assertTrue(logs.stream().noneMatch(line -> line.contains("CANARY")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test void saturatedExecutorReturnsSafeErrorWithoutInvokingHandlerOrRetrying() {
        stubUsageRecording();
        stubPrompt();
        stubMemorySettings();
        toolExecutor.shutdownNow();
        toolExecutor = new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.SynchronousQueue<>());
        service = new CompanionTurnServiceImpl(users, conversations, messages, model, contextBuilder, properties,
                transactions, quotaAvailability, quotaUsage, toolRegistry, memories, memoryRetrieval, screenContexts, toolExecutor);
        var busy = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        toolExecutor.submit(() -> {
            busy.countDown();
            awaitIgnoringInterrupt(release);
        });
        try {
            awaitLatch(busy);
            var prepared = prepareBusinessTurn("conversation-saturation",UUID.randomUUID().toString(),true);
            when(model.stream(any())).thenReturn(businessRound(List.of(new CompanionToolCall(
                    "path","getLearningPathProgress",java.util.Map.of("mode","ACTIVE"))),4),completedText("Unavailable",3));
            assertEquals("Unavailable",service.streamPreparedTurn(prepared,observer()).assistantMessage().content());
            var requests = org.mockito.ArgumentCaptor.forClass(CompanionModelRequest.class);
            verify(model,times(2)).stream(requests.capture());
            var result = requests.getAllValues().getLast().toolResults().getFirst();
            assertEquals("path",result.callId());
            assertTrue(result.json().contains("temporarily unavailable"));
            verify(toolRegistry,never()).execute(anyString(),anyMap(),anyString(),any(),any(),any());
            verify(quotaUsage).recordUsage(argThat(command -> command.amount() == 7));
        } finally {
            release.countDown();
        }
    }

    private CompanionTurnService.PreparedTurn prepareBusinessTurn(String id,String turnId,boolean finish) {
        if (finish) {
            when(conversations.findOwned(id,"learner-1")).thenReturn(Optional.of(conversation(id,0,1,null,null)),
                    Optional.of(conversation(id,2,3,null,null)));
            when(conversations.finish(eq(id),eq("learner-1"),eq(turnId),eq(1L),eq(2L),any())).thenReturn(true);
        } else {
            when(conversations.findOwned(id,"learner-1")).thenReturn(Optional.of(conversation(id,0,1,null,null)));
            when(conversations.release(eq(id),eq("learner-1"),eq(turnId),anyLong(),any())).thenReturn(true);
        }
        when(messages.findByTurnAndRole(eq(id),eq(turnId),any())).thenReturn(Optional.empty());
        when(conversations.admitNew(eq(id),eq("learner-1"),eq(turnId),eq(1L),any(),any(),eq(100)))
                .thenReturn(Optional.of(conversation(id,1,2,turnId,1L)));
        when(messages.insert(any())).thenAnswer(invocation -> {
            CompanionMessage value = invocation.getArgument(0);
            value.setId("message-"+value.getSequence());
            return value;
        });
        return service.prepareTurn(id,new CompanionTurnRequest(UUID.fromString(turnId),"Guide me",false));
    }

    private Flux<CompanionModelEvent> businessRound(List<CompanionToolCall> calls,long tokens) {
        return Flux.just(new CompanionModelEvent.ToolCalls(new CompanionModelContinuation() {},calls),
                new CompanionModelEvent.RoundCompleted(new CompanionUsage(tokens)));
    }

    private CompanionToolRegistry pathRegistry(fit.iuh.engolearn.learningpath.contract.LearningPathCompanionReads paths) {
        return new CompanionToolRegistry(List.of(new fit.iuh.engolearn.companion.service.impl.CompanionLearningPathToolHandler(
                paths,tools.jackson.databind.json.JsonMapper.builder().build())));
    }

    private fit.iuh.engolearn.learningpath.contract.dto.LearningPathActivePageDto latePathPage(Instant now) {
        var row = new fit.iuh.engolearn.learningpath.contract.dto.LearningPathActivePageDto.Row(
                "path","B1","IN_PROGRESS",1,List.of(),null,null,null,
                fit.iuh.engolearn.learningpath.contract.enums.LearningPathAvailability.UNKNOWN,List.of());
        return new fit.iuh.engolearn.learningpath.contract.dto.LearningPathActivePageDto(List.of(row),true,"late-cursor",
                fit.iuh.engolearn.learningpath.contract.enums.LearningPathCoverage.COMPLETE,now);
    }

    private static void awaitLatch(java.util.concurrent.CountDownLatch latch) {
        try {
            assertTrue(latch.await(5,java.util.concurrent.TimeUnit.SECONDS),"latch deadline");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void awaitIgnoringInterrupt(java.util.concurrent.CountDownLatch latch) {
        long deadline = System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (latch.getCount() != 0 && System.nanoTime() < deadline) {
            try {
                latch.await(100,java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                // Simulate a provider that cannot be interrupted immediately.
            }
        }
        assertEquals(0,latch.getCount(),"late provider release deadline");
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
