package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.model.enums.LearningPathStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathWeekStatus;
import fit.iuh.engolearn.learningpath.model.enums.LearningPathDayStatus;

import fit.iuh.engolearn.learningpath.model.LearningPath;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathAnswer;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathBaseline;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathBaselineTotal;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDay;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDayItem;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathDraft;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathInactivity;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathMicroSkillSnapshot;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathProgress;
import fit.iuh.engolearn.learningpath.model.vo.LearningPathWeek;
import fit.iuh.engolearn.learningpath.repository.LearningPathLifecycleRepository;
import fit.iuh.engolearn.learningpath.repository.LearningPathQuestionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningPathLifecycleEngineTest {

    private final LearningPathLifecycleEngine engine = new LearningPathLifecycleEngine();

    @Test
    void submittingAnEmptyDayMarksItSubmittedAndKeepsPracticeScoreEmpty() {
        LearningPath path = path("2026-10-09", "2026-10-09");
        startFirstDay(path, "2026-10-09", 0, 0);
        Instant now = Instant.parse("2026-10-09T03:00:00Z");
        engine.submit(path, 0, 0, now, Map.of());
        LearningPathDay day = path.getWeeks().getFirst().getDays().getFirst();
        assertThat(day.getStatus()).isEqualTo(LearningPathDayStatus.SUBMITTED);
        assertThat(day.getCloseReason().name()).isEqualTo("SUBMITTED");
        assertThat(day.getResult().getPracticeScore()).isNull();
        assertThat(day.getResult().getCompletionRate()).isZero();
        assertThat(path.getNextRunAt()).isNotNull();
        assertThat(path.getWeeks().getFirst().getStatus()).isEqualTo(LearningPathWeekStatus.OPEN);
    }

    @Test
    void submittedDayClosesWeekAtMidnightWithoutChangingItsResult() {
        LearningPath path = path("2026-10-09", "2026-10-09");
        startFirstDay(path, "2026-10-09", 2, 1);
        engine.submit(path, 0, 0, Instant.parse("2026-10-09T03:00:00Z"), Map.of());
        LearningPathDay day = path.getWeeks().getFirst().getDays().getFirst();
        assertThat(day.getStatus()).isEqualTo(LearningPathDayStatus.SUBMITTED);
        assertThat(day.getResult().getPracticeScore()).isEqualTo(50.0);
        assertThat(path.getWeeks().getFirst().getStatus()).isEqualTo(LearningPathWeekStatus.OPEN);
        engine.advance(path, midnight("2026-10-10"));
        assertThat(day.getStatus()).isEqualTo(LearningPathDayStatus.SUBMITTED);
        assertThat(day.getCloseReason().name()).isEqualTo("SUBMITTED");
        assertThat(day.getProgress().getAnswered()).isEqualTo(2);
        assertThat(path.getWeeks().getFirst().getStatus()).isEqualTo(LearningPathWeekStatus.CLOSED);
        assertThat(path.getWeeks().get(1).getStatus()).isEqualTo(LearningPathWeekStatus.OPEN);
    }

    @Test
    void submittingAllAnswersCompletesTheDayButWaitsForMidnight() {
        LearningPath path = path("2026-10-09", "2026-10-09");
        startFirstDay(path, "2026-10-09", 10, 8);
        var changes = engine.submit(path, 0, 0, Instant.parse("2026-10-09T03:00:00Z"), Map.of());
        LearningPathDay day = path.getWeeks().getFirst().getDays().getFirst();
        assertThat(day.getStatus()).isEqualTo(LearningPathDayStatus.COMPLETED);
        assertThat(day.getCloseReason().name()).isEqualTo("ALL_ANSWERED");
        assertThat(day.getResult().getPracticeScore()).isEqualTo(80.0);
        assertThat(changes.get("next_run_at")).isEqualTo(Date.from(midnight("2026-10-10")));
        assertThat(path.getWeeks().get(1).getStatus()).isEqualTo(LearningPathWeekStatus.LOCKED);
    }

    @Test
    void submittingLastFullyAnsweredDayCompletesWeekThreeImmediately() {
        LearningPath path = path("2026-10-01", "2026-10-07");
        engine.advance(path, midnight("2026-10-08"));
        engine.advance(path, midnight("2026-10-15"));
        path.setStatus(LearningPathStatus.IN_PROGRESS);
        LearningPathDay day = path.getWeeks().get(2).getDays().getFirst();
        day.setStatus(LearningPathDayStatus.IN_PROGRESS);
        day.setAutoCloseAt(Date.from(midnight("2026-10-16")));
        for (LearningPathDayItem item : day.getItems()) {
            LearningPathAnswer answer = new LearningPathAnswer();
            answer.setCorrect(true);
            item.setAnswer(answer);
        }
        Instant now = Instant.parse("2026-10-15T03:00:00Z");
        engine.submit(path, 2, 0, now, Map.of());
        assertThat(path.getStatus()).isEqualTo(LearningPathStatus.COMPLETED);
        assertThat(path.getWeeks().get(2).getCloseReason().name()).isEqualTo("ALL_ANSWERED");
        assertThat(path.getEndedAt()).isEqualTo(Date.from(now));
        assertThat(path.getSummary()).isNotNull();
    }

    @Test
    void closesUnstartedDayAndOpensNextWeekWithoutStartingPath() {
        LearningPath path = path("2026-10-01", "2026-10-01");

        engine.advance(path, midnight("2026-10-08"));

        assertThat(path.getWeeks().get(0).getDays().get(0).getStatus().name()).isEqualTo("CLOSED");
        assertThat(path.getWeeks().get(0).getDays().get(0).getCloseReason().name())
                .isEqualTo("WEEK_CLOSED");
        assertThat(path.getWeeks().get(0).getDays().get(0).getResult().getPracticeScore())
                .isNull();
        assertThat(path.getWeeks().get(0).getStats().getOverall().getCompletionRate())
                .isZero();
        assertThat(path.getWeeks().get(1).getStatus().name()).isEqualTo("OPEN");
        assertThat(path.getStatus().name()).isEqualTo("NOT_STARTED");
    }

    @Test
    void closesStartedDayAtMidnightUsingOnlySavedAnswers() {
        LearningPath path = path("2026-10-01", "2026-10-07");
        startFirstDay(path, "2026-10-07", 6, 4);

        engine.advance(path, midnight("2026-10-08"));

        LearningPathDay day = path.getWeeks().get(0).getDays().get(0);
        assertThat(day.getStatus().name()).isEqualTo("CLOSED");
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
        LearningPathDay day = path.getWeeks().getFirst().getDays().getFirst();
        day.getItems().get(0).setQuestionId("q-1");
        day.getItems().get(1).setQuestionId("q-2");
        for (int index = 0; index < 2; index++) {
            LearningPathDraft draft = new LearningPathDraft();
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
        LearningPathDay day = path.getWeeks().get(0).getDays().get(0);
        day.setStatus(LearningPathDayStatus.COMPLETED);
        day.setClosedAt(Date.from(Instant.parse("2026-10-09T03:00:00Z")));

        var beforeMidnight = engine.advance(path, Instant.parse("2026-10-09T16:59:59Z"));
        assertThat(beforeMidnight.get("next_run_at"))
                .isEqualTo(Date.from(midnight("2026-10-10")));
        assertThat(path.getWeeks().get(0).getStatus().name()).isEqualTo("OPEN");

        engine.advance(path, midnight("2026-10-10"));
        assertThat(path.getWeeks().get(0).getCloseReason().name()).isEqualTo("ALL_DAYS_DONE");
        assertThat(path.getWeeks().get(1).getStatus().name()).isEqualTo("OPEN");
    }

    @Test
    void catchUpCancelsAtEarlierHistoricalDeadlineBeforeWeekThreeExpiry() {
        LearningPath path = path("2026-10-01", "2026-10-01");
        Instant recoveredAt = midnight("2026-10-23");

        engine.advance(path, recoveredAt);
        engine.advance(path, recoveredAt);
        engine.advance(path, recoveredAt);

        assertThat(path.getStatus().name()).isEqualTo("CANCELLED");
        assertThat(path.getCancel().getAt()).isEqualTo(Date.from(midnight("2026-10-16")));
        assertThat(path.getWeeks().get(2).getStatus().name()).isEqualTo("OPEN");
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

        assertThat(path.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(path.getCancel()).isNull();
        assertThat(path.getSummary()).isNotNull();
        assertThat(path.getSummary().getCompletion().getAnswered()).isEqualTo(1);
    }

    @Test
    void serviceReplaysEveryMissedDeadlineInOneRun() {
        LearningPath path = path("2026-10-01", "2026-10-01");
        path.setId(new ObjectId().toHexString());
        Instant recoveredAt = midnight("2026-10-23");
        FakeRepository repository = new FakeRepository(path);

        new LearningPathLifecycleService(repository, mock(LearningPathQuestionRepository.class))
                .processDue(recoveredAt);

        assertThat(path.getStatus().name()).isEqualTo("CANCELLED");
        assertThat(repository.transitions).isEqualTo(3);
    }

    @Test
    void lifecycleServiceLoadsAnswerKeyToFinalizeDraft() {
        LearningPath path = path("2026-10-01", "2026-10-07");
        path.setId(new ObjectId().toHexString());
        path.setUserId(new ObjectId());
        startFirstDay(path, "2026-10-07", 0, 0);
        LearningPathDayItem item = path.getWeeks().getFirst().getDays().getFirst()
                .getItems().getFirst();
        item.setQuestionId("q-1");
        LearningPathDraft draft = new LearningPathDraft();
        draft.setSelectedOptionId("B");
        draft.setSavedAt(Date.from(midnight("2026-10-07")));
        item.setDraft(draft);
        LearningPathQuestionRepository questions = mock(LearningPathQuestionRepository.class);
        when(questions.findAnswerKeys(new ObjectId(path.getId()), path.getUserId(), List.of("q-1")))
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
        path.setStatus(LearningPathStatus.NOT_STARTED);
        path.setActive(true);
        path.setCurrentWeekNo(1);
        LearningPathProgress progress = new LearningPathProgress();
        progress.setAssigned(30);
        progress.setAnswered(0);
        progress.setCorrect(0);
        path.setProgress(progress);
        LearningPathInactivity inactivity = new LearningPathInactivity();
        inactivity.setAnchorDate(anchorDate);
        inactivity.setCancelAt(Date.from(LocalDate.parse(anchorDate).plusDays(15)
                .atStartOfDay(LearningPathLifecycleEngine.ZONE).toInstant()));
        path.setInactivity(inactivity);
        LearningPathBaseline baseline = new LearningPathBaseline();
        baseline.setPercent(40.0);
        LearningPathMicroSkillSnapshot skill = new LearningPathMicroSkillSnapshot();
        skill.setMicroSkill("Reading for Gist");
        skill.setBaseline(baseline);
        path.setMicroSkills(List.of(skill));
        LearningPathBaselineTotal baselineTotal = new LearningPathBaselineTotal();
        baselineTotal.setPercent(40.0);
        path.setBaselineTotal(baselineTotal);

        List<LearningPathWeek> weeks = new ArrayList<>();
        for (int number = 1; number <= 3; number++) {
            LearningPathWeek week = new LearningPathWeek();
            week.setWeekNo(number);
            week.setStatus(number == 1 ? LearningPathWeekStatus.OPEN : LearningPathWeekStatus.LOCKED);
            if (number == 1) {
                week.setOpenedAt(Date.from(midnight(createdDate)));
                week.setCloseAt(Date.from(LocalDate.parse(createdDate).plusDays(7)
                        .atStartOfDay(LearningPathLifecycleEngine.ZONE).toInstant()));
            }
            LearningPathDay day = new LearningPathDay();
            day.setDayNo(1);
            day.setStatus(LearningPathDayStatus.NOT_STARTED);
            day.setItems(new ArrayList<>());
            for (int order = 1; order <= 10; order++) {
                LearningPathDayItem item = new LearningPathDayItem();
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
        path.setStatus(LearningPathStatus.IN_PROGRESS);
        LearningPathDay day = path.getWeeks().get(0).getDays().get(0);
        day.setStatus(LearningPathDayStatus.IN_PROGRESS);
        day.setStartedDate(date);
        day.setAutoCloseAt(Date.from(LocalDate.parse(date).plusDays(1)
                .atStartOfDay(LearningPathLifecycleEngine.ZONE).toInstant()));
        for (int i = 0; i < answered; i++) {
            LearningPathAnswer answer = new LearningPathAnswer();
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
                             Integer expectedAnswered, Long revision, Map<String, Object> fields) {
            transitions++;
            path.setNextRunAt((Date) fields.get("next_run_at"));
            return true;
        }
    }
}
