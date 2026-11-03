package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.speaking.dto.response.SpeakingSessionResponse;
import fit.iuh.engolearn.speaking.model.*;
import fit.iuh.engolearn.speaking.repository.*;
import fit.iuh.engolearn.speaking.utils.port.ConversationEvaluator;
import fit.iuh.engolearn.speaking.utils.port.SpeakingAiGateway;
import fit.iuh.engolearn.speaking.utils.port.SpeechStreamPort;
import fit.iuh.engolearn.speaking.utils.port.SpeechSynthesisPort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingDemoRuntimeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void managerFlagIsEffectiveOnlyInsideTheExistingNonProductionDemoGate() throws Exception {
        assertFalse(effectiveFlag(false, true, "dev"));
        assertFalse(effectiveFlag(true, false, "dev"));
        assertTrue(effectiveFlag(true, true, "dev"));
        assertFalse(effectiveFlag(true, true, "prod"));
    }

    @Test
    void demoSettlementPersistsAcceptedEvidenceAndCountersOnlyOnce() {
        SettlementFixture fixture = SettlementFixture.active();
        fixture.turns.add(fixture.pendingTurn("turn-1", 1));
        fixture.session.setCommittedTurnCount(1);

        boolean first = settle(fixture.service(), "session", "turn-1", 7, validAssessment());
        boolean repeated = settle(fixture.service(), "session", "turn-1", 7, validAssessment());

        assertTrue(first);
        assertFalse(repeated);
        assertEquals(SpeakingTurn.EvaluationState.SUCCEEDED, fixture.turns.get(0).getEvaluationState());
        assertEquals(1, fixture.session.getValidTurnCount());
        assertEquals(1, fixture.assessments.size());
        assertEquals(List.of("goal-1"), fixture.session.getAchievedGoalIds());
    }

    @Test
    void validEvidenceWithoutAuthorizedReplyIsPersistedWithoutSpeech() {
        SettlementFixture fixture = SettlementFixture.active();
        fixture.turns.add(fixture.pendingTurn("turn-quiet", 1));
        fixture.session.setCommittedTurnCount(1);

        assertTrue(settle(fixture.service(), "session", "turn-quiet", 7, validNoReplyAssessment()));
        assertEquals(SpeakingTurn.EvaluationState.SUCCEEDED, fixture.turns.get(0).getEvaluationState());
        assertNull(fixture.turns.get(0).getAiReply());
        assertEquals(1, fixture.assessments.size());
        assertEquals(1, fixture.session.getValidTurnCount());
    }

    @Test
    void invalidAndEvaluationFailureUseSeparateStateAndThresholdSemantics() {
        SettlementFixture fixture = SettlementFixture.active();
        fixture.turns.add(fixture.pendingTurn("invalid", 1));
        fixture.session.setCommittedTurnCount(1);
        assertTrue(settle(fixture.service(), "session", "invalid", 7, invalidAssessment()));
        assertEquals(SpeakingTurn.EvaluationState.INVALID, fixture.turns.get(0).getEvaluationState());
        assertEquals(0, fixture.session.getValidTurnCount());

        for (int i = 1; i <= 3; i++) {
            String id = "failed-" + i;
            fixture.turns.add(fixture.pendingTurn(id, i + 1));
            fixture.session.setCommittedTurnCount(i + 1);
            assertTrue(settle(fixture.service(), "session", id, 7, failureAssessment(i)));
        }

        assertEquals(3, fixture.session.getConsecutiveEvaluationFailures());
        assertEquals(SpeakingSession.Status.INCOMPLETE, fixture.session.getStatus());
        assertEquals(SpeakingSession.EndReason.EVALUATION_FAILURES, fixture.session.getEndReason());
        assertTrue(fixture.assessments.isEmpty());
    }

    @Test
    void closingOrTerminalSessionRejectsLateSettlementWithoutChangingState() {
        SettlementFixture closing = SettlementFixture.active();
        closing.turns.add(closing.pendingTurn("late", 1));
        closing.session.setCommittedTurnCount(1);
        closing.session.setClosingAt(Instant.now());
        closing.session.setClosingReason(SpeakingSession.EndReason.LEARNER_ENDED);
        assertFalse(settle(closing.service(), "session", "late", 7, validAssessment()));
        assertEquals(SpeakingTurn.EvaluationState.PENDING, closing.turns.get(0).getEvaluationState());
        assertEquals(0, closing.session.getValidTurnCount());

        SettlementFixture terminal = SettlementFixture.active();
        terminal.session.setStatus(SpeakingSession.Status.INCOMPLETE);
        terminal.turns.add(terminal.pendingTurn("terminal", 1));
        terminal.session.setCommittedTurnCount(1);
        assertFalse(settle(terminal.service(), "session", "terminal", 7, validAssessment()));
        assertEquals(SpeakingTurn.EvaluationState.PENDING, terminal.turns.get(0).getEvaluationState());
    }

    @Test
    void replacedConnectionDuringDemoCompositionDoesNotSettleOrDeliver() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        CountDownLatch compositionStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        SpeakingDemoComposer composer = blockingComposer(gatewayCalls, compositionStarted, allowCompletion);
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        CountingTts tts = new CountingTts();
        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                tts, composer, true);

        realtime.open("connection", "session", "ticket", sessions.events::add).join();
        realtime.start("connection", "turn-1", 7);
        realtime.end("connection", "turn-1", 7);
        assertTrue(compositionStarted.await(1, TimeUnit.SECONDS));

        sessions.currentState.setControllerEpoch(8);
        realtime.open("replacement", "session", "ticket", sessions.events::add).join();
        allowCompletion.countDown();
        assertTrue(sessions.awaitSettlementIdle());

        assertEquals(1, gatewayCalls.get());
        assertEquals(0, sessions.settlementCalls.get());
        assertEquals(0, tts.calls.get());
        assertTrue(sessions.events.stream().noneMatch(event -> "TURN_RESULT".equals(event.get("type"))));
        assertTrue(sessions.events.stream().noneMatch(event -> "AI_AUDIO_READY".equals(event.get("type"))));
    }

    @Test
    void closedConnectionDuringDemoCompositionDoesNotSettleOrFailTheSession() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        CountDownLatch compositionStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        SpeakingDemoComposer composer = blockingFailingComposer(gatewayCalls, compositionStarted, allowCompletion);
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        CountingTts tts = new CountingTts();
        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                tts, composer, true);

        realtime.open("connection", "session", "ticket", sessions.events::add).join();
        realtime.start("connection", "turn-1", 7);
        realtime.end("connection", "turn-1", 7);
        assertTrue(compositionStarted.await(1, TimeUnit.SECONDS));

        realtime.close("connection");
        allowCompletion.countDown();
        assertTrue(sessions.awaitSettlementIdle());

        assertEquals(1, gatewayCalls.get());
        assertEquals(0, sessions.settlementCalls.get());
        assertEquals(0, sessions.failureCalls.get());
        assertTrue(sessions.failureEpochs.isEmpty());
        assertEquals(0, sessions.currentState.getConsecutiveEvaluationFailures());
        assertTrue(sessions.events.stream().noneMatch(event -> "PROCESSING_ERROR".equals(event.get("type"))));
    }

    @Test
    void compositionExceptionUsesEpochAwareFailurePathWhileControllerIsActive() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        CountDownLatch compositionStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        SpeakingDemoComposer composer = blockingFailingComposer(gatewayCalls, compositionStarted, allowCompletion);
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                new CountingTts(), composer, true);

        realtime.open("connection", "session", "ticket", sessions.events::add).join();
        realtime.start("connection", "turn-1", 7);
        realtime.end("connection", "turn-1", 7);
        assertTrue(compositionStarted.await(1, TimeUnit.SECONDS));
        allowCompletion.countDown();
        assertTrue(sessions.awaitSettlementIdle());

        assertEquals(1, gatewayCalls.get());
        assertEquals(1, sessions.failureCalls.get());
        assertEquals(List.of(7L), sessions.failureEpochs);
        assertTrue(sessions.events.stream().anyMatch(event -> "PROCESSING_ERROR".equals(event.get("type"))));
    }

    @Test
    void staleControllerEpochCannotSettleDemoTurn() {
        SettlementFixture fixture = SettlementFixture.active();
        fixture.turns.add(fixture.pendingTurn("stale", 1));
        fixture.session.setCommittedTurnCount(1);

        assertFalse(settle(fixture.service(), "session", "stale", 6, validAssessment()));
        assertEquals(SpeakingTurn.EvaluationState.PENDING, fixture.turns.get(0).getEvaluationState());
        assertEquals(0, fixture.session.getValidTurnCount());
        assertTrue(fixture.assessments.isEmpty());
    }

    @Test
    void demoSettlementRechecksExpiryBeforeCommittingLateGatewayResult() {
        SettlementFixture fixture = SettlementFixture.active();
        fixture.turns.add(fixture.pendingTurn("expired", 1));
        fixture.session.setCommittedTurnCount(1);
        fixture.session.setExpiresAt(Instant.now().minusSeconds(1));

        assertFalse(settle(fixture.service(), "session", "expired", 7, validAssessment()));
        assertEquals(SpeakingTurn.EvaluationState.PENDING, fixture.turns.get(0).getEvaluationState());
        assertEquals(0, fixture.session.getValidTurnCount());
        assertTrue(fixture.assessments.isEmpty());
    }

    @Test
    void concurrentOpensLeaveExactlyOneCurrentController() throws Exception {
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                new CountingTts(), composer(new AtomicInteger()), false);
        int openCount = 32;
        ExecutorService opens = Executors.newFixedThreadPool(openCount);
        CountDownLatch ready = new CountDownLatch(openCount);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < openCount; i++) {
                int connection = i;
                tasks.add(opens.submit(() -> {
                    ready.countDown();
                    assertTrue(release.await(1, TimeUnit.SECONDS));
                    realtime.open("connection-" + connection, "session", "ticket-" + connection,
                            sessions.events::add).join();
                    return null;
                }));
            }
            assertTrue(ready.await(1, TimeUnit.SECONDS));
            release.countDown();
            for (Future<?> task : tasks) task.get(2, TimeUnit.SECONDS);
        } finally {
            opens.shutdownNow();
        }

        Map<?, ?> currentControllers = privateMap(realtime, "currentControllers");
        assertEquals(1, currentControllers.size());
        assertTrue(privateBoolean(currentControllers.values().iterator().next(), "active"));
    }

    @Test
    void inactiveReplacedEntryDoesNotPreventDisconnectInterruption() throws Exception {
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                new CountingTts(), composer(new AtomicInteger()), false);

        realtime.open("old", "session", "ticket-old", sessions.events::add).join();
        realtime.open("new", "session", "ticket-new", sessions.events::add).join();
        realtime.close("new");
        invokeCleanup(realtime, "session", "learner", 7);

        assertEquals(1, sessions.interruptCalls.get());
        assertEquals(List.of(7L), sessions.interruptEpochs);
    }

    @Test
    void replacementDuringResultDeliveryCompletesAfterTheOldEventIsSerialized() throws Exception {
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                new CountingTts(), composer(new AtomicInteger()), true);
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch emitStarted = new CountDownLatch(1);
        CountDownLatch allowEmit = new CountDownLatch(1);
        Consumer<Map<String, Object>> oldEmit = event -> {
            if ("TURN_RESULT".equals(event.get("type"))) {
                emitStarted.countDown();
                try {
                    assertTrue(allowEmit.await(1, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            order.add("old:" + event.get("type"));
        };

        realtime.open("old", "session", "ticket-old", oldEmit).join();
        realtime.start("old", "turn-1", 7);
        realtime.end("old", "turn-1", 7);
        assertTrue(emitStarted.await(1, TimeUnit.SECONDS));

        CountDownLatch replacementStarted = new CountDownLatch(1);
        CompletableFuture<Void> replacement = CompletableFuture.runAsync(() -> {
            replacementStarted.countDown();
            realtime.open("new", "session", "ticket-new", event -> order.add("new:" + event.get("type"))).join();
        });
        assertTrue(replacementStarted.await(1, TimeUnit.SECONDS));
        try {
            replacement.get(100, TimeUnit.MILLISECONDS);
            fail("replacement must wait for the in-flight event delivery");
        } catch (java.util.concurrent.TimeoutException expected) {
            // The old event owns the per-session delivery slot until emit returns.
        }

        allowEmit.countDown();
        replacement.join();

        int resultIndex = order.indexOf("old:TURN_RESULT");
        int replacementIndex = order.indexOf("new:CONNECTED");
        assertTrue(resultIndex >= 0);
        assertTrue(replacementIndex > resultIndex);
    }

    @Test
    void demoSettlementAndFailureHaveNoPublicEpochBypass() {
        assertThrows(NoSuchMethodException.class, () -> SpeakingSessionService.class.getMethod("settleDemo",
                String.class, String.class, SpeakingDemoAssessment.class));
        assertThrows(NoSuchMethodException.class, () -> SpeakingSessionService.class.getMethod("assessmentFailed",
                String.class, String.class));
    }

    @Test
    void flagOffKeepsLegacyEvaluatorAndFlagOnUsesComposerDiagnosticsAndTts() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        SpeakingDemoComposer composer = composer(gatewayCalls);
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        AtomicInteger evaluatorCalls = new AtomicInteger();
        ConversationEvaluator evaluator = (content, transcript, history, achieved) -> {
            evaluatorCalls.incrementAndGet();
            return new ConversationEvaluator.Result(true, "Legacy reply", List.of(), List.of(), List.of(), null, List.of());
        };
        CountingTts tts = new CountingTts();

        SpeakingRealtimeService fallback = realtime(sessions, evaluator, tts, composer, false);
        runTurn(fallback, sessions);
        assertEquals(1, evaluatorCalls.get());
        assertEquals(0, gatewayCalls.get());
        assertTrue(sessions.events.stream().filter(event -> "TURN_RESULT".equals(event.get("type")))
                .allMatch(event -> !event.containsKey("demoDecision")));

        sessions.resetEvents();
        sessions.resetTurn();
        tts.calls.set(0);
        SpeakingRealtimeService demo = realtime(sessions, evaluator, tts, composer, true);
        runTurn(demo, sessions);
        Map<String, Object> result = sessions.events.stream()
                .filter(event -> "TURN_RESULT".equals(event.get("type"))).findFirst().orElseThrow();
        assertEquals(1, gatewayCalls.get());
        assertEquals(1, sessions.settlementCalls.get());
        assertTrue(result.containsKey("demoDecision"));
        assertEquals(DialogueDecision.Outcome.NORMAL,
                ((SpeakingDemoDecisionEvent) result.get("demoDecision")).outcome());
        assertEquals(1, tts.calls.get());
    }

    @Test
    void staleControllerAndTerminalDecisionNeverReachNormalSpeech() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        SpeakingDemoComposer composer = composer(gatewayCalls);
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        CountingTts tts = new CountingTts();
        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                tts, composer, true);

        realtime.open("connection", "session", "ticket", sessions.events::add).join();
        sessions.currentState.setControllerEpoch(99);
        realtime.start("connection", "turn-1", 7);
        realtime.end("connection", "turn-1", 7);

        assertEquals(0, gatewayCalls.get());
        assertEquals(0, sessions.settlementCalls.get());
        assertEquals(0, tts.calls.get());
        assertTrue(sessions.events.stream().noneMatch(event -> "AI_AUDIO_READY".equals(event.get("type"))));
    }

    @Test
    void evaluationFailureEmitsProcessingErrorDiagnosticWithoutTts() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        SpeakingDemoComposer composer = failingComposer(gatewayCalls);
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        CountingTts tts = new CountingTts();

        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                tts, composer, true);
        runTurn(realtime, sessions);

        Map<String, Object> error = sessions.events.stream()
                .filter(event -> "PROCESSING_ERROR".equals(event.get("type"))).findFirst().orElseThrow();
        assertEquals(1, gatewayCalls.get());
        assertEquals(1, sessions.settlementCalls.get());
        assertTrue(error.get("demoDecision") instanceof SpeakingDemoDecisionEvent);
        assertEquals(DialogueDecision.Outcome.RECOVERY,
                ((SpeakingDemoDecisionEvent) error.get("demoDecision")).outcome());
        assertEquals(0, tts.calls.get());
    }

    @Test
    void terminalManagerDecisionCommitsDiagnosticsButSuppressesSpeech() throws Exception {
        AtomicInteger gatewayCalls = new AtomicInteger();
        SpeakingDemoComposer composer = composer(gatewayCalls);
        FakeRealtimeSessionService sessions = FakeRealtimeSessionService.active();
        sessions.currentState.setAchievedGoalIds(new ArrayList<>(List.of("goal-1", "goal-2")));
        CountingTts tts = new CountingTts();

        SpeakingRealtimeService realtime = realtime(sessions, (content, transcript, history, achieved) ->
                new ConversationEvaluator.Result(true, "legacy", List.of(), List.of(), List.of(), null, List.of()),
                tts, composer, true);
        runTurn(realtime, sessions);

        Map<String, Object> result = sessions.events.stream()
                .filter(event -> "TURN_RESULT".equals(event.get("type"))).findFirst().orElseThrow();
        assertEquals(1, gatewayCalls.get());
        assertEquals(DialogueDecision.Outcome.TERMINAL,
                ((SpeakingDemoDecisionEvent) result.get("demoDecision")).outcome());
        assertEquals(0, tts.calls.get());
    }

    private static boolean effectiveFlag(boolean demo, boolean manager, String profile) throws Exception {
        Class<?> type = Class.forName("fit.iuh.engolearn.config.SpeakingDemoManagerConfig");
        Constructor<?> constructor = type.getConstructor(org.springframework.core.env.Environment.class,
                boolean.class, boolean.class);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
        environment.setActiveProfiles(profile);
        Object config = constructor.newInstance(environment, demo, manager);
        return (boolean) type.getMethod("enabled").invoke(config);
    }

    private static boolean settle(SpeakingSessionService service, String sessionId, String turnId,
            long epoch, SpeakingDemoAssessment assessment) {
        try {
            Method method = SpeakingSessionService.class.getMethod("settleDemo", String.class, String.class,
                    long.class, SpeakingDemoAssessment.class);
            return (boolean) method.invoke(service, sessionId, turnId, epoch, assessment);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("demo settlement API is missing", exception);
        }
    }

    private static void invokeCleanup(SpeakingRealtimeService realtime, String sessionId, String learnerId,
            long epoch) throws Exception {
        Method method = SpeakingRealtimeService.class.getDeclaredMethod("cleanupDisconnectedSession",
                String.class, String.class, long.class);
        method.setAccessible(true);
        method.invoke(realtime, sessionId, learnerId, epoch);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> privateMap(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(target);
    }

    private static boolean privateBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static SpeakingRealtimeService realtime(SpeakingSessionService sessions,
            ConversationEvaluator evaluator, SpeechSynthesisPort tts, SpeakingDemoComposer composer,
            boolean enabled) throws Exception {
        for (Constructor<?> constructor : SpeakingRealtimeService.class.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 6) {
                constructor.setAccessible(true);
                return (SpeakingRealtimeService) constructor.newInstance(sessions, stream(), evaluator, tts,
                        composer, enabled);
            }
        }
        throw new AssertionError("demo realtime constructor is missing");
    }

    private static void runTurn(SpeakingRealtimeService realtime, FakeRealtimeSessionService sessions) {
        realtime.open("connection", "session", "ticket", sessions.events::add).join();
        realtime.start("connection", "turn-1", 7);
        realtime.end("connection", "turn-1", 7);
        for (int i = 0; i < 100 && sessions.events.stream().noneMatch(event ->
                "TURN_RESULT".equals(event.get("type")) || "PROCESSING_ERROR".equals(event.get("type"))); i++) {
            try { Thread.sleep(10); } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail(exception);
            }
        }
    }

    private static SpeakingDemoComposer composer(AtomicInteger gatewayCalls) {
        SpeakingAiGateway gateway = request -> {
            gatewayCalls.incrementAndGet();
            String raw = "{\"contractVersion\":\"speaking.ai.draft.v1\","
                    + "\"assessment\":{\"turnValidityProposal\":\"VALID\","
                    + "\"userIntent\":\"STATEMENT\",\"goalEvidence\":[],"
                    + "\"resourceEvidence\":[],\"corrections\":[],\"hintVi\":null},"
                    + "\"dialogue\":{\"proposedAct\":\"REACT_AND_ASK\","
                    + "\"spokenReply\":\"Keep going.\",\"continuationType\":\"QUESTION\","
                    + "\"primaryQuestion\":\"Why?\",\"targetGoalId\":null}}";
            return new GatewayOutcome.Success(new ProviderRawDraft(raw,
                    new ProviderRawDraft.ProviderMetadata("test", 200, "id", "STOP", 1L, 1L, 2L)));
        };
        return new SpeakingDemoComposer(gateway, new SpeakingDraftValidator(MAPPER), new ConversationManager());
    }

    private static SpeakingDemoComposer failingComposer(AtomicInteger gatewayCalls) {
        SpeakingAiGateway gateway = request -> {
            gatewayCalls.incrementAndGet();
            return new GatewayOutcome.TransportFailure(new GatewayOutcome.ProviderFailure(
                    GatewayOutcome.FailureCode.TRANSPORT, "offline", null, null, null));
        };
        return new SpeakingDemoComposer(gateway, new SpeakingDraftValidator(MAPPER), new ConversationManager());
    }

    private static SpeakingDemoComposer blockingComposer(AtomicInteger gatewayCalls,
            CountDownLatch compositionStarted, CountDownLatch allowCompletion) {
        SpeakingAiGateway gateway = request -> {
            gatewayCalls.incrementAndGet();
            compositionStarted.countDown();
            try {
                assertTrue(allowCompletion.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("composition interrupted", exception);
            }
            return new GatewayOutcome.Success(new ProviderRawDraft(
                    "{\"contractVersion\":\"speaking.ai.draft.v1\","
                            + "\"assessment\":{\"turnValidityProposal\":\"VALID\","
                            + "\"userIntent\":\"STATEMENT\",\"goalEvidence\":[],"
                            + "\"resourceEvidence\":[],\"corrections\":[],\"hintVi\":null},"
                            + "\"dialogue\":{\"proposedAct\":\"REACT_AND_ASK\","
                            + "\"spokenReply\":\"Keep going.\",\"continuationType\":\"QUESTION\","
                            + "\"primaryQuestion\":\"Why?\",\"targetGoalId\":null}}",
                    new ProviderRawDraft.ProviderMetadata("test", 200, "id", "STOP", 1L, 1L, 2L)));
        };
        return new SpeakingDemoComposer(gateway, new SpeakingDraftValidator(MAPPER), new ConversationManager());
    }

    private static SpeakingDemoComposer blockingFailingComposer(AtomicInteger gatewayCalls,
            CountDownLatch compositionStarted, CountDownLatch allowCompletion) {
        SpeakingAiGateway gateway = request -> {
            gatewayCalls.incrementAndGet();
            compositionStarted.countDown();
            try {
                assertTrue(allowCompletion.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("composition interrupted", exception);
            }
            throw new IllegalStateException("offline");
        };
        return new SpeakingDemoComposer(gateway, new SpeakingDraftValidator(MAPPER), new ConversationManager());
    }

    private static SpeakingDemoAssessment validAssessment() {
        ValidatedTurnProposal proposal = ValidatedTurnProposal.valid(
                SpeakingAiDraftResponseV1.UserIntent.STATEMENT, List.of("goal-1"),
                new SpeakingAiDraftResponseV1.Dialogue(DialogueAct.REACT_AND_ASK, "Keep going.",
                        SpeakingAiDraftResponseV1.ContinuationType.QUESTION, "Why?", null));
        return new SpeakingDemoAssessment(proposal, new DialogueDecision(DialogueDecision.Outcome.NORMAL,
                DialogueDecision.ReasonCode.NORMAL, DialogueAct.REACT_AND_ASK, "Keep going.", null,
                DialogueDecision.SystemTrigger.NONE, SpeakingSession.Status.ACTIVE, 1, 1, 0,
                List.of("goal-1"), false, false, false, false));
    }

    private static SpeakingDemoAssessment invalidAssessment() {
        return new SpeakingDemoAssessment(ValidatedTurnProposal.invalid(), new DialogueDecision(
                DialogueDecision.Outcome.RECOVERY, DialogueDecision.ReasonCode.INVALID_TURN, null, null, null,
                DialogueDecision.SystemTrigger.NONE, SpeakingSession.Status.ACTIVE, 1, 0, 0, List.of(), false,
                false, false, false));
    }

    private static SpeakingDemoAssessment validNoReplyAssessment() {
        ValidatedTurnProposal proposal = new ValidatedTurnProposal(
                ValidatedTurnProposal.ValidityOutcome.VALID,
                List.of(new SpeakingAssessmentRecord.GoalEvidence("goal-1", "I order food")),
                List.of(), List.of(), null, null,
                SpeakingAiDraftResponseV1.UserIntent.STATEMENT, List.of());
        return new SpeakingDemoAssessment(proposal, new DialogueDecision(DialogueDecision.Outcome.NORMAL,
                DialogueDecision.ReasonCode.DIALOGUE_NOT_AVAILABLE, null, null, null,
                DialogueDecision.SystemTrigger.NONE, SpeakingSession.Status.ACTIVE, 1, 1, 0,
                List.of("goal-1"), false, false, false, false));
    }

    private static SpeakingDemoAssessment failureAssessment(int errors) {
        return new SpeakingDemoAssessment(ValidatedTurnProposal.evaluationFailed(), new DialogueDecision(
                errors >= 3 ? DialogueDecision.Outcome.TERMINAL : DialogueDecision.Outcome.RECOVERY,
                errors >= 3 ? DialogueDecision.ReasonCode.EVALUATION_ERRORS_3
                        : DialogueDecision.ReasonCode.EVALUATION_ERROR,
                null, null, null, DialogueDecision.SystemTrigger.NONE,
                errors >= 3 ? SpeakingSession.Status.INCOMPLETE : SpeakingSession.Status.ACTIVE,
                errors + 1, 0, errors, List.of(), false, false, false, false));
    }

    private static SpeakingSession session() {
        return SpeakingSession.builder().id("session").learnerId("learner").openLearnerId("learner")
                .status(SpeakingSession.Status.ACTIVE).controllerEpoch(7).startedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1200)).achievedGoalIds(new ArrayList<>())
                .content(new SpeakingContentSnapshot("item", "version", "Cafe", "Order", List.of(
                        new SpeakingGoal("goal-1", "Order", "order food", SpeakingGoal.Priority.REQUIRED),
                        new SpeakingGoal("goal-2", "Ask", "ask price", SpeakingGoal.Priority.REQUIRED)),
                        null, 1, List.of(), List.of(), 1, "REQUIRED_GOALS_V2")).build();
    }

    private static final class SettlementFixture {
        private final SpeakingSession session = session();
        private final List<SpeakingTurn> turns = new ArrayList<>();
        private final Map<String, SpeakingAssessmentRecord> assessments = new HashMap<>();
        private final List<SpeakingUsageSegment> usage = new ArrayList<>();

        static SettlementFixture active() { return new SettlementFixture(); }

        SpeakingTurn pendingTurn(String id, int sequence) {
            return SpeakingTurn.builder().id(id).sessionId("session").sequence(sequence).transcript("I order food")
                    .committedAt(Instant.now()).evaluationState(SpeakingTurn.EvaluationState.PENDING).build();
        }

        SpeakingSessionService service() {
            CurrentUserProvider users = proxy(CurrentUserProvider.class, (name, args) -> "learner");
            SpeakingSessionRepository sessions = proxy(SpeakingSessionRepository.class, (name, args) -> {
                if (name.equals("findById")) return Optional.of(session);
                if (name.equals("save")) return args[0];
                return List.of();
            });
            SpeakingTurnRepository turnRepository = proxy(SpeakingTurnRepository.class, (name, args) -> {
                if (name.equals("findByIdAndSessionId")) return turns.stream()
                        .filter(turn -> turn.getId().equals(args[0]) && turn.getSessionId().equals(args[1])).findFirst();
                if (name.equals("findById")) return turns.stream().filter(turn -> turn.getId().equals(args[0])).findFirst();
                if (name.equals("findBySessionIdOrderBySequenceAsc")) return turns;
                if (name.equals("save")) return args[0];
                return Optional.empty();
            });
            SpeakingAssessmentRepository assessmentRepository = proxy(SpeakingAssessmentRepository.class,
                    (name, args) -> {
                        if (name.equals("findById")) return Optional.ofNullable(assessments.get(args[0]));
                        if (name.equals("save")) {
                            SpeakingAssessmentRecord record = (SpeakingAssessmentRecord) args[0];
                            record.setId(record.getId() == null ? record.getTranscript() : record.getId());
                            assessments.put(record.getId(), record);
                            return record;
                        }
                        return Optional.empty();
                    });
            SpeakingUsageSegmentRepository usageRepository = proxy(SpeakingUsageSegmentRepository.class,
                    (name, args) -> {
                        if (name.equals("save")) { usage.add((SpeakingUsageSegment) args[0]); return args[0]; }
                        return List.of();
                    });
            SpeakingAudioAssetRepository audio = proxy(SpeakingAudioAssetRepository.class, (name, args) -> List.of());
            return new SpeakingSessionService(users, null, null, sessions, turnRepository, assessmentRepository,
                    audio, usageRepository, proxy(MediaStorage.class, (name, args) -> null));
        }
    }

    private static final class FakeRealtimeSessionService extends SpeakingSessionService {
        private final SpeakingSession session = session();
        private final SpeakingSession currentState = session();
        private final SpeakingTurn turn = SpeakingTurn.builder().id("turn-1").sessionId("session")
                .sequence(1).transcript("I order food").committedAt(Instant.now())
                .evaluationState(SpeakingTurn.EvaluationState.PENDING).build();
        private final SpeakingSessionResponse snapshot = new SpeakingSessionResponse("session", session.getContent(),
                SpeakingSession.Status.ACTIVE, null, Instant.EPOCH, Instant.EPOCH, null, 0, 1, List.of(), List.of(),
                false, false, 7, 1L, true, null, List.of());
        private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
        private final AtomicInteger settlementCalls = new AtomicInteger();
        private final AtomicInteger failureCalls = new AtomicInteger();
        private final List<Long> failureEpochs = new CopyOnWriteArrayList<>();
        private final AtomicInteger interruptCalls = new AtomicInteger();
        private final List<Long> interruptEpochs = new CopyOnWriteArrayList<>();
        private FakeRealtimeSessionService() { super(null, null, null, null, null, null, null, null, null); }
        static FakeRealtimeSessionService active() { return new FakeRealtimeSessionService(); }
        void resetEvents() { events.clear(); }
        void resetTurn() { currentState.setControllerEpoch(7); }
        boolean awaitSettlementIdle() throws InterruptedException {
            Thread.sleep(100);
            return true;
        }
        public SpeakingSession consumeTicket(String ticket, String sessionId) { return currentState; }
        public SpeakingSessionResponse socketSnapshot(String id, String learnerId) { return snapshot; }
        public SpeakingSession requireSocketController(String id, String learnerId, long epoch) { return currentState; }
        public SpeakingTurn commit(String sessionId, String learnerId, long epoch, String turnId, String transcript) { return turn; }
        public void recordUsage(String sessionId, String turnId, SpeakingUsageSegment.Kind kind, Instant started, Instant ended) {}
        public void assessed(String sessionId, String turnId, ConversationEvaluator.Result result) {}
        public void assessmentFailed(String sessionId, String turnId, long epoch) {
            if (epoch != currentState.getControllerEpoch() || !currentState.isOpen()) return;
            failureCalls.incrementAndGet();
            failureEpochs.add(epoch);
            currentState.setConsecutiveEvaluationFailures(currentState.getConsecutiveEvaluationFailures() + 1);
        }
        public void saveAudio(String sessionId, String turnId, SpeakingAudioAsset.Kind kind, String format,
                String contentType, byte[] bytes) {}
        public SpeakingSession demoState(String sessionId, String learnerId) { return currentState; }
        public boolean settleDemo(String sessionId, String turnId, long epoch, SpeakingDemoAssessment assessment) {
            if (epoch != currentState.getControllerEpoch() || !currentState.isOpen()) return false;
            settlementCalls.incrementAndGet();
            return true;
        }
        public void interruptIfDisconnected(String sessionId, String learnerId, long epoch) {
            interruptCalls.incrementAndGet();
            interruptEpochs.add(epoch);
        }
    }

    private static final class CountingTts implements SpeechSynthesisPort {
        private final AtomicInteger calls = new AtomicInteger();
        public byte[] synthesize(String text) { calls.incrementAndGet(); return new byte[] {1}; }
    }

    private static SpeechStreamPort stream() {
        return new SpeechStreamPort() {
            public CompletableFuture<Void> open(String id, Listener listener) { return CompletableFuture.completedFuture(null); }
            public void beginTurn(String id) {}
            public void send(String id, byte[] pcm) {}
            public CompletableFuture<String> endTurn(String id) { return CompletableFuture.completedFuture("I order food"); }
            public void cancelTurn(String id) {}
            public void close(String id) {}
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> invoke) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, arguments) -> invoke.apply(method.getName(), arguments == null ? new Object[0] : arguments));
    }
}
