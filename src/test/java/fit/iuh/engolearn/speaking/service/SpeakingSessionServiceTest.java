package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.model.*;
import fit.iuh.engolearn.speaking.repository.*;
import fit.iuh.engolearn.topic.contract.SpeakingItemLookup;
import fit.iuh.engolearn.topic.contract.dto.SpeakingItemDto;
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
        assertEquals(3, response.content().requiredGoalCount());
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

    private static final class Fixture {
        String learner = "learner";
        SpeakingItemDto item;
        SpeakingItemConfig config;
        SpeakingSession savedSession;
        int audioReads;
        List<SpeakingUsageSegment> segments = new ArrayList<>();

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
            SpeakingTurnRepository turns = proxy(SpeakingTurnRepository.class, (name, args) -> List.of());
            SpeakingAssessmentRepository assessments = proxy(SpeakingAssessmentRepository.class,
                    (name, args) -> Optional.empty());
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
