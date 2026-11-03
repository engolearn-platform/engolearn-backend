package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.model.*;
import fit.iuh.engolearn.speaking.repository.*;
import fit.iuh.engolearn.topic.contract.SpeakingItemLookup;
import fit.iuh.engolearn.topic.contract.dto.SpeakingItemDto;
import fit.iuh.engolearn.speaking.utils.port.ConversationEvaluator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingSessionServiceTest {
    @Test void createPinsTheItemVersionAndGoalThreshold() {
        Fixture fixture = new Fixture();
        fixture.item = new SpeakingItemDto("item", "version-2", "At a cafe", "Order lunch",
                List.of("menu"), List.of("May I have..."));
        fixture.config = SpeakingItemConfig.builder().topicItemVersionId("version-2")
                .goals(List.of(new SpeakingGoal("g1", "Order", "Food", SpeakingGoal.Priority.REQUIRED),
                        new SpeakingGoal("g2", "Ask", "Price", SpeakingGoal.Priority.OPTIONAL),
                        new SpeakingGoal("g3", "Thank", "Waiter", SpeakingGoal.Priority.REQUIRED)))
                .goalPercentage(67).minimumValidTurns(5).revision(4)
                .status(SpeakingItemConfig.Status.PUBLISHED).build();

        var response = fixture.service().create("item", "request-1", false);

        assertEquals("version-2", response.content().topicItemVersionId());
        assertEquals(4, response.content().configRevision());
        assertEquals("REQUIRED_GOALS_V2", response.content().completionPolicyVersion());
        assertNull(response.content().goalPercentage());
        assertEquals(2, response.content().requiredGoalCount());
        assertEquals("learner", fixture.savedSession.getLearnerId());
        assertEquals("learner", fixture.savedSession.getOpenLearnerId());
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

    private static final class Fixture {
        String learner = "learner";
        SpeakingItemDto item;
        SpeakingItemConfig config;
        SpeakingSession savedSession;
        int audioReads;
        List<SpeakingUsageSegment> segments = new ArrayList<>();
        List<SpeakingTurn> turns = new ArrayList<>();
        Map<String, SpeakingAssessmentRecord> assessmentRecords = new HashMap<>();

        SpeakingSessionService service() {
            CurrentUserProvider users = proxy(CurrentUserProvider.class, (name, args) ->
                    name.equals("requireCurrentUserId") ? learner : Optional.empty());
            SpeakingItemLookup items = proxy(SpeakingItemLookup.class, (name, args) -> Optional.ofNullable(item));
            SpeakingItemConfigRepository configs = proxy(SpeakingItemConfigRepository.class,
                    (name, args) -> Optional.ofNullable(config));
            SpeakingSessionRepository sessions = proxy(SpeakingSessionRepository.class, (name, args) -> {
                if (name.equals("findById")) return savedSession != null
                        && savedSession.getId().equals(args[0]) ? Optional.of(savedSession) : Optional.empty();
                if (name.equals("save")) { savedSession = (SpeakingSession) args[0]; return savedSession; }
                if (name.equals("findFirstByLearnerIdAndStatusInAndDeletedAtIsNull")) return Optional.empty();
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
                return List.of();
            });
            SpeakingUsageSegmentRepository usage = proxy(SpeakingUsageSegmentRepository.class, (name, args) -> {
                if (name.equals("save")) { segments.add((SpeakingUsageSegment) args[0]); return args[0]; }
                return List.of();
            });
            MediaStorage media = proxy(MediaStorage.class, (name, args) -> null);
            return new SpeakingSessionService(users, items, configs, sessions,
                    turns, assessments, audio, usage, media);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> invoke) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (instance, method, arguments) -> invoke.apply(method.getName(), arguments));
    }
}
