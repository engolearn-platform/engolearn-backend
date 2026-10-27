package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.repository.LearningPathLifecycleRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningPathLifecycleEngineTest {

    private final LearningPathLifecycleEngine engine = new LearningPathLifecycleEngine();

    @Test
    void closesUnstartedDayAndOpensNextWeekWithoutStartingPath() {
        LearningPath path = path("2026-10-01", "2026-10-01");

        engine.advance(path, midnight("2026-10-08"));

        assertThat(path.getWeeks().get(0).getDays().get(0).getStatus()).isEqualTo("CLOSED");
        assertThat(path.getWeeks().get(0).getDays().get(0).getCloseReason())
                .isEqualTo("WEEK_CLOSED");
        assertThat(path.getWeeks().get(0).getDays().get(0).getResult().getPracticeScore())
                .isNull();
        assertThat(path.getWeeks().get(0).getStats().getOverall().getCompletionRate())
                .isZero();
        assertThat(path.getWeeks().get(1).getStatus()).isEqualTo("OPEN");
        assertThat(path.getStatus()).isEqualTo("NOT_STARTED");
    }

    @Test
    void closesStartedDayAtMidnightUsingOnlySavedAnswers() {
        LearningPath path = path("2026-10-01", "2026-10-07");
        startFirstDay(path, "2026-10-07", 6, 4);

        engine.advance(path, midnight("2026-10-08"));

        LearningPath.Day day = path.getWeeks().get(0).getDays().get(0);
        assertThat(day.getStatus()).isEqualTo("CLOSED");
        assertThat(day.getResult().getWrong()).isEqualTo(2);
        assertThat(day.getResult().getCompletionRate()).isEqualTo(60.0);
        assertThat(day.getResult().getPracticeScore()).isCloseTo(66.6666666667,
                org.assertj.core.data.Offset.offset(0.00001));
        assertThat(path.getWeeks().get(0).getStats().getOverall().getAnswered()).isEqualTo(6);
    }

    @Test
    void midnightFinalizesDraftsBeforeComputingProgressAndLeavesBlankQuestionsUnanswered() {
        LearningPath path = path("2026-10-01", "2026-10-07");
        startFirstDay(path, "2026-10-07", 0, 0);
        LearningPath.Day day = path.getWeeks().getFirst().getDays().getFirst();
        day.getItems().get(0).setQuestionId("q-1");
        day.getItems().get(1).setQuestionId("q-2");
        for (int index = 0; index < 2; index++) {
            LearningPath.Draft draft = new LearningPath.Draft();
            draft.setSelectedOptionId(index == 0 ? "A" : "B");
            draft.setSavedAt(Date.from(midnight("2026-10-07")));
            day.getItems().get(index).setDraft(draft);
        }

        Map<String, Object> changes = engine.advance(path, midnight("2026-10-08"),
                Map.of("q-1", "A", "q-2", "A"));

        assertThat(day.getItems().get(0).getAnswer().getCorrect()).isTrue();
        assertThat(day.getItems().get(1).getAnswer().getCorrect()).isFalse();
        assertThat(day.getItems().get(0).getDraft()).isNull();
        assertThat(day.getItems().get(2).getAnswer()).isNull();
        assertThat(day.getProgress().getAnswered()).isEqualTo(2);
        assertThat(path.getProgress().getAnswered()).isEqualTo(2);
        assertThat(day.getResult().getCompletionRate()).isEqualTo(20.0);
        assertThat(day.getResult().getPracticeScore()).isEqualTo(50.0);
        assertThat(changes).containsKeys("weeks.0.days.0.items.0.answer",
                "weeks.0.days.0.items.0.draft", "progress");
    }

    @Test
    void finishedWeekWaitsForNextMidnight() {
        LearningPath path = path("2026-10-09", "2026-10-09");
        startFirstDay(path, "2026-10-09", 10, 8);
        LearningPath.Day day = path.getWeeks().get(0).getDays().get(0);
        day.setStatus("COMPLETED");
        day.setClosedAt(Date.from(Instant.parse("2026-10-09T03:00:00Z")));

        var beforeMidnight = engine.advance(path, Instant.parse("2026-10-09T16:59:59Z"));
        assertThat(beforeMidnight.get("next_run_at"))
                .isEqualTo(Date.from(midnight("2026-10-10")));
        assertThat(path.getWeeks().get(0).getStatus()).isEqualTo("OPEN");

        engine.advance(path, midnight("2026-10-10"));
        assertThat(path.getWeeks().get(0).getCloseReason()).isEqualTo("ALL_DAYS_DONE");
        assertThat(path.getWeeks().get(1).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void catchUpCancelsAtEarlierHistoricalDeadlineBeforeWeekThreeExpiry() {
        LearningPath path = path("2026-10-01", "2026-10-01");
        Instant recoveredAt = midnight("2026-10-23");

        engine.advance(path, recoveredAt);
        engine.advance(path, recoveredAt);
        engine.advance(path, recoveredAt);

        assertThat(path.getStatus()).isEqualTo("CANCELLED");
        assertThat(path.getCancel().getAt()).isEqualTo(Date.from(midnight("2026-10-16")));
        assertThat(path.getWeeks().get(2).getStatus()).isEqualTo("OPEN");
        assertThat(path.getSummary()).isNull();
    }

    @Test
    void weekThreeExpiryWinsWhenCancellationHasSameDeadline() {
        LearningPath path = path("2026-10-01", "2026-10-07");
        startFirstDay(path, "2026-10-07", 1, 1);
        Instant recoveredAt = midnight("2026-10-23");

        engine.advance(path, recoveredAt);
        engine.advance(path, recoveredAt);
        engine.advance(path, recoveredAt);

        assertThat(path.getStatus()).isEqualTo("COMPLETED");
        assertThat(path.getCancel()).isNull();
        assertThat(path.getSummary()).isNotNull();
        assertThat(path.getSummary().getCompletion().getAnswered()).isEqualTo(1);
    }

    @Test
    void serviceReplaysEveryMissedDeadlineInOneRun() {
        LearningPath path = path("2026-10-01", "2026-10-01");
        path.setId(new ObjectId());
        Instant recoveredAt = midnight("2026-10-23");
        FakeRepository repository = new FakeRepository(path);

        new LearningPathLifecycleService(repository, mock(LearningPathQuestionRepository.class))
                .processDue(recoveredAt);

        assertThat(path.getStatus()).isEqualTo("CANCELLED");
        assertThat(repository.transitions).isEqualTo(3);
    }

    @Test
    void lifecycleServiceLoadsAnswerKeyToFinalizeDraft() {
        LearningPath path = path("2026-10-01", "2026-10-07");
        path.setId(new ObjectId());
        path.setUserId(new ObjectId());
        startFirstDay(path, "2026-10-07", 0, 0);
        LearningPath.DayItem item = path.getWeeks().getFirst().getDays().getFirst()
                .getItems().getFirst();
        item.setQuestionId("q-1");
        LearningPath.Draft draft = new LearningPath.Draft();
        draft.setSelectedOptionId("B");
        draft.setSavedAt(Date.from(midnight("2026-10-07")));
        item.setDraft(draft);
        LearningPathQuestionRepository questions = mock(LearningPathQuestionRepository.class);
        when(questions.findAnswerKeys(path.getId(), path.getUserId(), List.of("q-1")))
                .thenReturn(List.of(new Document("question_id", "q-1")
                        .append("correct_option_id", "B")));

        new LearningPathLifecycleService(new FakeRepository(path), questions)
                .processDue(midnight("2026-10-08"));

        assertThat(item.getAnswer().getCorrect()).isTrue();
        assertThat(item.getDraft()).isNull();
        assertThat(path.getProgress().getAnswered()).isEqualTo(1);
    }

    private LearningPath path(String createdDate, String anchorDate) {
        LearningPath path = new LearningPath();
        path.setCreatedDate(createdDate);
        path.setStatus("NOT_STARTED");
        path.setActive(true);
        path.setCurrentWeekNo(1);
        LearningPath.Progress progress = new LearningPath.Progress();
        progress.setAssigned(30);
        progress.setAnswered(0);
        progress.setCorrect(0);
        path.setProgress(progress);
        LearningPath.Inactivity inactivity = new LearningPath.Inactivity();
        inactivity.setAnchorDate(anchorDate);
        inactivity.setCancelAt(Date.from(LocalDate.parse(anchorDate).plusDays(15)
                .atStartOfDay(LearningPathLifecycleEngine.ZONE).toInstant()));
        path.setInactivity(inactivity);
        LearningPath.Baseline baseline = new LearningPath.Baseline();
        baseline.setPercent(40.0);
        LearningPath.MicroSkillSnapshot skill = new LearningPath.MicroSkillSnapshot();
        skill.setMicroSkill("Reading for Gist");
        skill.setBaseline(baseline);
        path.setMicroSkills(List.of(skill));
        LearningPath.BaselineTotal baselineTotal = new LearningPath.BaselineTotal();
        baselineTotal.setPercent(40.0);
        path.setBaselineTotal(baselineTotal);

        List<LearningPath.Week> weeks = new ArrayList<>();
        for (int number = 1; number <= 3; number++) {
            LearningPath.Week week = new LearningPath.Week();
            week.setWeekNo(number);
            week.setStatus(number == 1 ? "OPEN" : "LOCKED");
            if (number == 1) {
                week.setOpenedAt(Date.from(midnight(createdDate)));
                week.setCloseAt(Date.from(LocalDate.parse(createdDate).plusDays(7)
                        .atStartOfDay(LearningPathLifecycleEngine.ZONE).toInstant()));
            }
            LearningPath.Day day = new LearningPath.Day();
            day.setDayNo(1);
            day.setStatus("NOT_STARTED");
            day.setItems(new ArrayList<>());
            for (int order = 1; order <= 10; order++) {
                LearningPath.DayItem item = new LearningPath.DayItem();
                item.setOrder(order);
                item.setMicroSkill("Reading for Gist");
                day.getItems().add(item);
            }
            week.setDays(List.of(day));
            weeks.add(week);
        }
        path.setWeeks(weeks);
        path.setNextRunAt(weeks.get(0).getCloseAt());
        return path;
    }

    private void startFirstDay(LearningPath path, String date, int answered, int correct) {
        path.setStatus("IN_PROGRESS");
        LearningPath.Day day = path.getWeeks().get(0).getDays().get(0);
        day.setStatus("IN_PROGRESS");
        day.setStartedDate(date);
        day.setAutoCloseAt(Date.from(LocalDate.parse(date).plusDays(1)
                .atStartOfDay(LearningPathLifecycleEngine.ZONE).toInstant()));
        for (int i = 0; i < answered; i++) {
            LearningPath.Answer answer = new LearningPath.Answer();
            answer.setCorrect(i < correct);
            day.getItems().get(i).setAnswer(answer);
        }
        path.getProgress().setAnswered(answered);
        path.getProgress().setCorrect(correct);
    }

    private Instant midnight(String date) {
        return LocalDate.parse(date).atStartOfDay(LearningPathLifecycleEngine.ZONE).toInstant();
    }

    private static final class FakeRepository extends LearningPathLifecycleRepository {

        private final LearningPath path;
        private int transitions;

        private FakeRepository(LearningPath path) {
            super(null);
            this.path = path;
        }

        @Override
        public List<LearningPath> findDue(Instant now, int limit, List<ObjectId> excludedIds) {
            return Boolean.TRUE.equals(path.getActive()) && path.getNextRunAt() != null
                    && !path.getNextRunAt().toInstant().isAfter(now)
                    ? List.of(path) : List.of();
        }

        @Override
        public Optional<LearningPath> findById(ObjectId id) {
            return Optional.of(path);
        }

        @Override
        public boolean apply(ObjectId id, String expectedStatus, Date expectedNextRunAt,
                             Integer expectedAnswered, Map<String, Object> fields) {
            transitions++;
            path.setNextRunAt((Date) fields.get("next_run_at"));
            return true;
        }
    }
}
