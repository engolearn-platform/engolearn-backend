package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.model.*;
import fit.iuh.engolearn.speaking.repository.*;
import fit.iuh.engolearn.topic.contract.SpeakingItemLookup;
import fit.iuh.engolearn.topic.contract.dto.SpeakingItemDto;
import fit.iuh.engolearn.speaking.utils.port.ConversationEvaluator;
import fit.iuh.engolearn.speaking.utils.port.SpeakingOpeningGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingSessionServiceTest {
    @Test void invalidOpeningConfigDoesNotReplaceOpenSession() {
        for (int missingField = 0; missingField < 4; missingField++) {
            Fixture fixture = new Fixture();
            fixture.item = new SpeakingItemDto("item", "version-2", "At a cafe", "Order lunch",
                    List.of("menu"), List.of("May I have..."));
            SpeakingItemConfig.SpeakingItemConfigBuilder<?, ?> builder = SpeakingItemConfig.builder()
                    .topicItemVersionId("version-2")
                    .goals(List.of(new SpeakingGoal("g1", "Order", "Food", SpeakingGoal.Priority.REQUIRED),
                            new SpeakingGoal("g2", "Ask", "Price", SpeakingGoal.Priority.OPTIONAL),
                            new SpeakingGoal("g3", "Thank", "Waiter", SpeakingGoal.Priority.REQUIRED)))
                    .context("A cafe").learnerRole("waiter").aiRole("customer")
                    .openingSide(SpeakingItemConfig.OpeningSide.LEARNER)
                    .minimumValidTurns(3).revision(4).status(SpeakingItemConfig.Status.PUBLISHED);
            switch (missingField) {
                case 0 -> builder.context(" ");
                case 1 -> builder.learnerRole(null);
                case 2 -> builder.aiRole("");
                case 3 -> builder.openingSide(null);
                default -> throw new AssertionError();
            }
            fixture.config = builder.build();
            fixture.existingSession = SpeakingSession.builder().id("old-session").learnerId("learner")
                    .openLearnerId("learner").status(SpeakingSession.Status.ACTIVE).controllerEpoch(1)
                    .achievedGoalIds(new ArrayList<>()).build();

            SpeakingException error = assertThrows(SpeakingException.class,
                    () -> fixture.service().create("item", "request-new", true));

            assertEquals("CONFIG_INVALID", error.code());
            assertEquals("CONFIG_INVALID", assertThrows(SpeakingException.class,
                    () -> fixture.service().create("item", "request-new", false)).code());
            assertEquals(SpeakingSession.Status.ACTIVE, fixture.existingSession.getStatus());
            assertEquals("old-session", fixture.existingSession.getId());
            assertEquals(0, fixture.sessionWrites);
        }
    }

    @Test void createPinsTheItemVersionAndGoalThreshold() {
        Fixture fixture = new Fixture();
        fixture.item = new SpeakingItemDto("item", "version-2", "At a cafe", "Order lunch",
                List.of("menu"), List.of("May I have..."));
        fixture.config = SpeakingItemConfig.builder().topicItemVersionId("version-2")
                .goals(List.of(new SpeakingGoal("g1", "Order", "Food", SpeakingGoal.Priority.REQUIRED),
                        new SpeakingGoal("g2", "Ask", "Price", SpeakingGoal.Priority.OPTIONAL),
                        new SpeakingGoal("g3", "Thank", "Waiter", SpeakingGoal.Priority.REQUIRED)))
                .goalPercentage(67).minimumValidTurns(5).revision(4)
                .context("You are in a cafe. The waiter wants to help a customer choose a drink.")
                .learnerRole("waiter").aiRole("customer")
                .openingSide(SpeakingItemConfig.OpeningSide.LEARNER)
                .status(SpeakingItemConfig.Status.PUBLISHED).build();

        var response = fixture.service().create("item", "request-1", false);

        assertEquals("version-2", response.content().topicItemVersionId());
        assertEquals(4, response.content().configRevision());
        assertEquals("REQUIRED_GOALS_V2", response.content().completionPolicyVersion());
        assertNull(response.content().goalPercentage());
        assertEquals(2, response.content().requiredGoalCount());
        assertEquals("You are in a cafe. The waiter wants to help a customer choose a drink.",
                response.content().context());
        assertEquals("waiter", response.content().learnerRole());
        assertEquals("customer", response.content().aiRole());
        assertEquals(SpeakingItemConfig.OpeningSide.LEARNER, response.content().openingSide());
        assertEquals(SpeakingOpening.Status.NOT_REQUIRED, response.opening().status());
        assertTrue(response.canStartTurn());
        assertTrue(fixture.openingRequests().isEmpty());
        assertEquals("learner", fixture.savedSession.getLearnerId());
        assertEquals("learner", fixture.savedSession.getOpenLearnerId());
    }

    @Test void aiOpeningStartsOnceAndBlocksLearnerUntilTheAudioStageSettles() {
        Fixture fixture = new Fixture();
        fixture.item = new SpeakingItemDto("item", "version", "Cafe", "Order", List.of(), List.of());
        fixture.config = SpeakingItemConfig.builder().topicItemVersionId("version")
                .goals(activeFixture("REQUIRED_GOALS_V2", null, 3).savedSession.getContent().goals())
                .minimumValidTurns(3).context("Order a drink").learnerRole("customer").aiRole("waiter")
                .openingSide(SpeakingItemConfig.OpeningSide.AI).status(SpeakingItemConfig.Status.PUBLISHED).build();
        SpeakingSessionService service = fixture.service();

        var created = service.create("item", "session", false);
        var duplicate = service.create("item", "session", false);
        assertEquals(SpeakingOpening.Status.GENERATING, created.opening().status());
        assertEquals(created.opening(), duplicate.opening());
        assertEquals(1, fixture.openingRequests().size());
        assertFalse(created.canStartTurn());
        assertEquals("OPENING_PENDING", assertThrows(SpeakingException.class,
                () -> service.requireSocketController("session", "learner", 1)).code());

        var attempt = fixture.openingRequests().getFirst();
        assertTrue(service.acceptOpeningDraft(attempt, draft()));
        assertFalse(service.get("session").canStartTurn());
        service.settleOpeningAudio(attempt, null);

        var fallback = service.resume("session", "resume-1");
        assertEquals(SpeakingOpening.Status.TEXT_ONLY, fallback.opening().status());
        assertEquals(draft().reply(), fallback.opening().reply());
        assertEquals(draft().hintExampleEn(), fallback.opening().hintExampleEn());
        assertTrue(fallback.canStartTurn());
        assertEquals(0, fallback.committedTurnCount());
        assertEquals(0, fallback.validTurnCount());
        assertEquals(1, fixture.openingRequests().size());
    }

    @Test void retryRequiresCurrentControllerAndNeverReusesARequestId() {
        Fixture fixture = openingFixture();
        SpeakingSessionService service = fixture.service();
        service.failOpening(currentAttempt(fixture));
        assertEquals(SpeakingOpening.Status.FAILED, service.get("session").opening().status());

        assertEquals("CONTROLLER_CHANGED", assertThrows(SpeakingException.class,
                () -> service.retryOpening("session", 0, "retry-a")).code());
        assertEquals(SpeakingOpening.Status.GENERATING, service.retryOpening("session", 1, "retry-a").opening().status());
        service.retryOpening("session", 1, "retry-a");
        var firstRetry = fixture.openingRequests().getFirst();
        service.failOpening(firstRetry);
        service.retryOpening("session", 1, "retry-b");
        var secondRetry = fixture.openingRequests().getLast();
        assertFalse(service.acceptOpeningDraft(firstRetry, draft()));
        service.failOpening(secondRetry);
        service.retryOpening("session", 1, "retry-a");
        assertEquals(2, fixture.openingRequests().size());
        assertEquals(SpeakingOpening.Status.FAILED, service.get("session").opening().status());
    }

    @Test void socketTicketCannotAcquireControlAfterSessionResumesElsewhere() {
        Fixture fixture = openingFixture();
        SpeakingSessionService service = fixture.service();
        String ticket = service.ticket("session", 1).ticket();

        service.resume("session", "other-tab");

        assertEquals("CONTROLLER_CHANGED", assertThrows(SpeakingException.class,
                () -> service.consumeTicket(ticket, "session")).code());
        assertEquals(2, service.consumeTicket(service.ticket("session", 2).ticket(),
                "session").getControllerEpoch());
    }

    @Test void reconcilesOpeningWritesAfterAcknowledgementFailure() {
        Fixture fixture = openingFixture();
        SpeakingSessionService service = fixture.service();
        var attempt = currentAttempt(fixture);
        fixture.failNextSessionAcknowledgement = true;

        assertThrows(DataAccessResourceFailureException.class, () -> service.acceptOpeningDraft(attempt, draft()));
        assertTrue(service.acceptOpeningDraft(attempt, draft()));
        assertEquals(SpeakingOpening.Status.SYNTHESIZING, service.get("session").opening().status());

        fixture.failNextSessionAcknowledgement = true;
        assertThrows(DataAccessResourceFailureException.class, () -> service.settleOpeningAudio(attempt, openingAsset()));
        service.settleOpeningAudio(attempt, openingAsset());
        assertEquals(SpeakingOpening.Status.READY, service.get("session").opening().status());
        assertEquals(2, fixture.events.stream().filter(SpeakingSessionService.OpeningUpdated.class::isInstance).count());
        assertEquals(1, fixture.audioAssets.size());
    }

    @Test void persistedInvalidOpeningSideReturnsConfigInvalid() {
        Fixture fixture = new Fixture();
        fixture.item = new SpeakingItemDto("item", "version", "Cafe", "Order", List.of(), List.of());
        fixture.configReadFailure = new IllegalArgumentException("No enum constant OpeningSide.PARTNER");

        assertEquals("CONFIG_INVALID", assertThrows(SpeakingException.class,
                () -> fixture.service().create("item", "new", false)).code());
        assertEquals(0, fixture.sessionWrites);
    }

    @Test void openingAudioIsAvailableToOwnerUntilExpiryOrSoftDeletion() {
        Fixture fixture = openingFixture();
        SpeakingSessionService service = fixture.service();
        var attempt = currentAttempt(fixture);
        service.acceptOpeningDraft(attempt, draft());
        var asset = openingAsset();
        service.settleOpeningAudio(attempt, asset);

        var ready = service.get("session");
        assertEquals(SpeakingOpening.Status.READY, ready.opening().status());
        assertTrue(ready.opening().aiAudioAvailable());
        assertNotNull(service.openingPlayback("session").url());
        fixture.learner = "intruder";
        assertEquals("SESSION_FORBIDDEN", assertThrows(SpeakingException.class,
                () -> service.openingPlayback("session")).code());
        fixture.learner = "learner";
        asset.setPlaybackUntil(Instant.now().minusSeconds(1));
        assertFalse(service.get("session").opening().aiAudioAvailable());
        assertEquals("AUDIO_EXPIRED", assertThrows(SpeakingException.class,
                () -> service.openingPlayback("session")).code());
        fixture.savedSession.setDeletedAt(Instant.now());
        assertEquals("SESSION_FORBIDDEN", assertThrows(SpeakingException.class,
                () -> service.openingPlayback("session")).code());
    }

    @Test void recoveryCancelsPendingOpeningAndRejectsLateAudio() {
        Fixture fixture = openingFixture();
        SpeakingSessionService service = fixture.service();
        var attempt = currentAttempt(fixture);
        service.acceptOpeningDraft(attempt, draft());

        service.recoverAfterRestart();
        service.settleOpeningAudio(attempt, openingAsset());

        var snapshot = service.get("session");
        assertEquals(SpeakingSession.EndReason.SERVICE_RESTART, snapshot.endReason());
        assertEquals(SpeakingOpening.Status.CANCELLED, snapshot.opening().status());
        assertFalse(snapshot.canStartTurn());
        assertNull(snapshot.opening().hintExampleEn());
        assertTrue(fixture.audioAssets.isEmpty());
    }

    private static Fixture openingFixture() {
        Fixture fixture = activeFixture("REQUIRED_GOALS_V2", null, 3);
        fixture.savedSession.setOpening(SpeakingOpening.initial(SpeakingItemConfig.OpeningSide.AI));
        return fixture;
    }

    private static SpeakingSessionService.OpeningRequested currentAttempt(Fixture fixture) {
        return new SpeakingSessionService.OpeningRequested("session",
                fixture.savedSession.getOpening().attemptId(), fixture.savedSession.getContent());
    }

    private static SpeakingOpeningGenerator.Draft draft() {
        return new SpeakingOpeningGenerator.Draft("Can I get you a drink?", "I'd like an iced coffee.");
    }

    private static SpeakingAudioAsset openingAsset() {
        return SpeakingAudioAsset.builder().id("session:opening").sessionId("session")
                .kind(SpeakingAudioAsset.Kind.AI_OPENING).cloudinaryPublicId("opening")
                .format("mp3").playbackUntil(Instant.now().plusSeconds(60)).build();
    }

    @Test void audioIsRejectedBeforeLookupWhenAnotherLearnerOwnsTheSession() {
        Fixture fixture = new Fixture();
        fixture.learner = "intruder";
        fixture.savedSession = SpeakingSession.builder().id("session").learnerId("owner").build();

        SpeakingException error = assertThrows(SpeakingException.class, () ->
                fixture.service().playback("session", "turn", SpeakingAudioAsset.Kind.LEARNER_RECORDING));

        assertEquals("SESSION_FORBIDDEN", error.code());
        assertEquals(0, fixture.audioReads);
    }

    @Test void usageSplitsAtVietnameseMidnightWithoutRepeatingIds() {
        Fixture fixture = new Fixture();
        fixture.savedSession = SpeakingSession.builder().id("session").learnerId("learner").build();

        fixture.service().recordUsage("session", "turn", SpeakingUsageSegment.Kind.LEARNER_SPEECH,
                Instant.parse("2026-10-28T16:59:59Z"), Instant.parse("2026-10-28T17:00:01Z"));

        assertEquals(List.of(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 29)),
                fixture.segments.stream().map(SpeakingUsageSegment::getDay).toList());
        assertEquals(List.of(1000L, 1000L),
                fixture.segments.stream().map(SpeakingUsageSegment::getDurationMillis).toList());
        assertEquals(List.of("turn:LEARNER_SPEECH:0", "turn:LEARNER_SPEECH:1"),
                fixture.segments.stream().map(SpeakingUsageSegment::getId).toList());
    }

    @Test void endingAnEmptySessionKeepsItInHistoryAsIncomplete() {
        Fixture fixture = new Fixture();
        fixture.savedSession = SpeakingSession.builder().id("session").learnerId("learner")
                .openLearnerId("learner").status(SpeakingSession.Status.ACTIVE)
                .controllerEpoch(3).achievedGoalIds(List.of())
                .content(new SpeakingContentSnapshot("item", "version", "At a cafe", "Order lunch",
                        List.of(new SpeakingGoal("g1", "Order", "Food", SpeakingGoal.Priority.REQUIRED)),
                        100, 3, List.of(), List.of(), 1)).build();

        var result = fixture.service().end("session", 3);

        assertEquals(SpeakingSession.Status.INCOMPLETE, result.status());
        assertEquals(SpeakingSession.EndReason.LEARNER_ENDED, result.endReason());
        assertTrue(result.turns().isEmpty());
        assertNull(fixture.savedSession.getOpenLearnerId());
    }

    @Test void v2RequiresEveryRequiredGoalAndMinimumTurnsButOptionalGoalControlsAutoEnd() {
        Fixture fixture = activeFixture("REQUIRED_GOALS_V2", null, 3);
        fixture.savedSession.setValidTurnCount(2);
        fixture.savedSession.setAchievedGoalIds(new ArrayList<>(List.of("optional", "required-1")));
        var service = fixture.service();
        assertFalse(service.get("session").requiredGoalsMet());
        assertFalse(service.get("session").completionEligible());

        fixture.savedSession.setValidTurnCount(3);
        fixture.savedSession.setAchievedGoalIds(new ArrayList<>(List.of("required-1", "required-2")));
        assertTrue(service.get("session").requiredGoalsMet());
        assertTrue(service.get("session").completionEligible());
        assertEquals(SpeakingSession.Status.ACTIVE, service.get("session").status());

        fixture.turns.add(pendingTurn("turn-3", 3));
        service.assessed("session", "turn-3", validResult("optional"));
        assertEquals(SpeakingSession.Status.COMPLETED, fixture.savedSession.getStatus());
        assertEquals(SpeakingSession.EndReason.GOALS_REACHED, fixture.savedSession.getEndReason());
    }

    @Test void legacySessionKeepsPinnedRatioEvenWhenRequiredFlagsDiffer() {
        Fixture fixture = activeFixture(null, 66, 3);
        fixture.savedSession.setValidTurnCount(3);
        fixture.savedSession.setAchievedGoalIds(new ArrayList<>(List.of("optional", "required-1")));
        var response = fixture.service().get("session");
        assertTrue(response.content().legacyRatioPolicy());
        assertEquals(2, response.content().requiredGoalCount());
        assertTrue(response.requiredGoalsMet());
        assertTrue(response.completionEligible());
        assertFalse(response.goalResults().get(2).achieved());
        assertEquals(SpeakingSession.Status.COMPLETED, fixture.service().end("session", 1).status());
    }

    @Test void finalizedInvalidAndEvaluationFailureHaveDistinctCounters() {
        Fixture fixture = activeFixture("REQUIRED_GOALS_V2", null, 3);
        var service = fixture.service();
        fixture.turns.add(pendingTurn("invalid", 1));
        fixture.savedSession.setCommittedTurnCount(1);
        service.assessed("session", "invalid", new ConversationEvaluator.Result(false, null,
                List.of(), List.of(), List.of(), null, List.of()));
        assertEquals(SpeakingTurn.EvaluationState.INVALID, fixture.turns.get(0).getEvaluationState());
        assertEquals(1, fixture.savedSession.getCommittedTurnCount());
        assertEquals(0, fixture.savedSession.getValidTurnCount());
        assertEquals(0, fixture.savedSession.getConsecutiveEvaluationFailures());

        fixture.turns.add(pendingTurn("failed", 2));
        fixture.savedSession.setCommittedTurnCount(2);
        service.assessmentFailed("session", "failed", 1);
        assertEquals(SpeakingTurn.EvaluationState.FAILED, fixture.turns.get(1).getEvaluationState());
        assertEquals(2, fixture.savedSession.getCommittedTurnCount());
        assertEquals(0, fixture.savedSession.getValidTurnCount());
        assertEquals(1, fixture.savedSession.getConsecutiveEvaluationFailures());

        fixture.turns.add(pendingTurn("valid", 3));
        fixture.savedSession.setCommittedTurnCount(3);
        service.assessed("session", "valid", validResult());
        assertEquals(3, fixture.savedSession.getCommittedTurnCount());
        assertEquals(1, fixture.savedSession.getValidTurnCount());
        assertEquals(0, fixture.savedSession.getConsecutiveEvaluationFailures());
        assertEquals(3, service.get("session").turns().size());
    }

    @Test void staleControllerEpochCannotRecordDemoAssessmentFailure() {
        Fixture fixture = activeFixture("REQUIRED_GOALS_V2", null, 3);
        fixture.turns.add(pendingTurn("stale-failure", 1));
        fixture.savedSession.setCommittedTurnCount(1);

        fixture.service().assessmentFailed("session", "stale-failure", 0);

        assertEquals(SpeakingTurn.EvaluationState.PENDING, fixture.turns.get(0).getEvaluationState());
        assertEquals(0, fixture.savedSession.getConsecutiveEvaluationFailures());
        assertEquals(SpeakingSession.Status.ACTIVE, fixture.savedSession.getStatus());
    }

    @Test void demoRecoveryReleasesPendingTurnWithoutChangingFailureSemantics() {
        Fixture fixture = activeFixture("REQUIRED_GOALS_V2", null, 3);
        fixture.savedSession.setConsecutiveEvaluationFailures(2);
        fixture.turns.add(pendingTurn("replaced", 1));
        fixture.savedSession.setCommittedTurnCount(1);
        SpeakingSessionService service = fixture.service();

        assertTrue(invokeDemoRecovery(service, "session", "replaced", 1));

        assertEquals(SpeakingTurn.EvaluationState.FAILED, fixture.turns.get(0).getEvaluationState());
        assertEquals(2, fixture.savedSession.getConsecutiveEvaluationFailures());
        assertEquals(SpeakingSession.Status.ACTIVE, fixture.savedSession.getStatus());
        SpeakingTurn next = service.commit("session", "learner", 1, "next", "Hello again");
        assertEquals(2, next.getSequence());
    }

    @Test void pendingFinalizedTurnWithinClosingWindowCanCompleteWithoutReplacingFirstReason() {
        Fixture fixture = activeFixture("REQUIRED_GOALS_V2", null, 3);
        fixture.savedSession.setCommittedTurnCount(29);
        fixture.savedSession.setValidTurnCount(2);
        fixture.savedSession.setAchievedGoalIds(new ArrayList<>(List.of("required-1")));
        var service = fixture.service();

        service.commit("session", "learner", 1, "turn-30", "Thank you");
        assertEquals(30, fixture.savedSession.getCommittedTurnCount());
        assertEquals(SpeakingSession.EndReason.TURN_LIMIT, fixture.savedSession.getClosingReason());
        assertEquals(SpeakingSession.Status.ACTIVE, fixture.savedSession.getStatus());
        assertFalse(service.get("session").canStartTurn());

        service.assessed("session", "turn-30", validResult("required-2"));
        assertEquals(3, fixture.savedSession.getValidTurnCount());
        assertEquals(SpeakingSession.Status.COMPLETED, fixture.savedSession.getStatus());
        assertEquals(SpeakingSession.EndReason.TURN_LIMIT, fixture.savedSession.getEndReason());
        assertEquals(SpeakingSession.EndReason.TURN_LIMIT, service.end("session", 1).endReason());
    }

    private static Fixture activeFixture(String policy, Integer percentage, int minimumTurns) {
        Fixture fixture = new Fixture();
        var goals = List.of(new SpeakingGoal("required-1", "Order", "Food", SpeakingGoal.Priority.REQUIRED),
                new SpeakingGoal("optional", "Ask", "Price", SpeakingGoal.Priority.OPTIONAL),
                new SpeakingGoal("required-2", "Thank", "Waiter", SpeakingGoal.Priority.REQUIRED));
        fixture.savedSession = SpeakingSession.builder().id("session").learnerId("learner")
                .openLearnerId("learner").status(SpeakingSession.Status.ACTIVE).controllerEpoch(1)
                .startedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1200))
                .achievedGoalIds(new ArrayList<>())
                .content(new SpeakingContentSnapshot("item", "version", "Cafe", "Order", goals,
                        percentage, minimumTurns, List.of(), List.of(), 1, policy)).build();
        return fixture;
    }

    private static SpeakingTurn pendingTurn(String id, int sequence) {
        return SpeakingTurn.builder().id(id).sessionId("session").sequence(sequence)
                .transcript("Hello").committedAt(Instant.now())
                .evaluationState(SpeakingTurn.EvaluationState.PENDING).build();
    }

    private static ConversationEvaluator.Result validResult(String... goalIds) {
        return new ConversationEvaluator.Result(true, null,
                Arrays.stream(goalIds).map(id -> new SpeakingAssessmentRecord.GoalEvidence(id, "Hello")).toList(),
                List.of(), List.of(), null, List.of());
    }

    private static boolean invokeDemoRecovery(SpeakingSessionService service, String sessionId, String turnId,
            long controllerEpoch) {
        try {
            Method method = SpeakingSessionService.class.getMethod("recoverPendingConversationTurn",
                    String.class, String.class, long.class);
            return (boolean) method.invoke(service, sessionId, turnId, controllerEpoch);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("demo recovery API is missing", exception);
        }
    }

    private static final class Fixture {
        String learner = "learner";
        SpeakingItemDto item;
        SpeakingItemConfig config;
        SpeakingSession savedSession;
        SpeakingSession existingSession;
        int sessionWrites;
        int audioReads;
        RuntimeException configReadFailure;
        boolean failNextSessionAcknowledgement;
        List<SpeakingUsageSegment> segments = new ArrayList<>();
        List<SpeakingTurn> turns = new ArrayList<>();
        Map<String, SpeakingAssessmentRecord> assessmentRecords = new HashMap<>();
        Map<String, SpeakingAudioAsset> audioAssets = new HashMap<>();
        List<Object> events = new ArrayList<>();

        List<SpeakingSessionService.OpeningRequested> openingRequests() {
            return events.stream().filter(SpeakingSessionService.OpeningRequested.class::isInstance)
                    .map(SpeakingSessionService.OpeningRequested.class::cast).toList();
        }

        SpeakingSessionService service() {
            CurrentUserProvider users = proxy(CurrentUserProvider.class, (name, args) ->
                    name.equals("requireCurrentUserId") ? learner : Optional.empty());
            SpeakingItemLookup items = proxy(SpeakingItemLookup.class, (name, args) -> Optional.ofNullable(item));
            SpeakingItemConfigRepository configs = proxy(SpeakingItemConfigRepository.class, (name, args) -> {
                if (configReadFailure != null) throw configReadFailure;
                return Optional.ofNullable(config);
            });
            SpeakingSessionRepository sessions = proxy(SpeakingSessionRepository.class, (name, args) -> {
                if (name.equals("findById")) return savedSession != null
                        && savedSession.getId().equals(args[0]) ? Optional.of(savedSession) : Optional.empty();
                if (name.equals("save")) {
                    sessionWrites++;
                    savedSession = (SpeakingSession) args[0];
                    if (failNextSessionAcknowledgement) {
                        failNextSessionAcknowledgement = false;
                        throw new DataAccessResourceFailureException("Acknowledgement lost");
                    }
                    return savedSession;
                }
                if (name.equals("findFirstByLearnerIdAndStatusInAndDeletedAtIsNull")) {
                    return Optional.ofNullable(existingSession);
                }
                if (name.equals("findByStatusIn") && savedSession != null && savedSession.isOpen()) {
                    return List.of(savedSession);
                }
                return List.of();
            });
            SpeakingTurnRepository turns = proxy(SpeakingTurnRepository.class, (name, args) -> {
                if (name.equals("findBySessionIdOrderBySequenceAsc")) return this.turns;
                if (name.equals("findByIdAndSessionId")) return this.turns.stream()
                        .filter(turn -> turn.getId().equals(args[0]) && turn.getSessionId().equals(args[1])).findFirst();
                if (name.equals("save")) {
                    var turn = (SpeakingTurn) args[0];
                    if (this.turns.stream().noneMatch(existing -> existing.getId().equals(turn.getId()))) this.turns.add(turn);
                    return turn;
                }
                return Optional.empty();
            });
            SpeakingAssessmentRepository assessments = proxy(SpeakingAssessmentRepository.class,
                    (name, args) -> {
                        if (name.equals("findById")) return Optional.ofNullable(assessmentRecords.get(args[0]));
                        if (name.equals("save")) {
                            var record = (SpeakingAssessmentRecord) args[0];
                            assessmentRecords.put(record.getId(), record);
                            return record;
                        }
                        return Optional.empty();
                    });
            SpeakingAudioAssetRepository audio = proxy(SpeakingAudioAssetRepository.class, (name, args) -> {
                audioReads++;
                if (name.equals("findById")) return Optional.ofNullable(audioAssets.get(args[0]));
                if (name.equals("save")) {
                    var asset = (SpeakingAudioAsset) args[0];
                    audioAssets.put(asset.getId(), asset);
                    return asset;
                }
                return List.of();
            });
            SpeakingUsageSegmentRepository usage = proxy(SpeakingUsageSegmentRepository.class, (name, args) -> {
                if (name.equals("save")) { segments.add((SpeakingUsageSegment) args[0]); return args[0]; }
                return List.of();
            });
            MediaStorage media = proxy(MediaStorage.class, (name, args) -> URI.create("https://example.test/audio"));
            SpeakingSessionService service = new SpeakingSessionService(users, items, configs, sessions,
                    turns, assessments, audio, usage, media);
            ReflectionTestUtils.setField(service, "events", (ApplicationEventPublisher) events::add);
            return service;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> invoke) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (instance, method, arguments) -> invoke.apply(method.getName(), arguments));
    }
}
