package fit.iuh.engolearn.assessment;

import fit.iuh.engolearn.assessment.mapper.AssessmentMapper;
import fit.iuh.engolearn.assessment.model.AssessmentAttempt;
import fit.iuh.engolearn.assessment.model.AssessmentTimeline;
import fit.iuh.engolearn.assessment.model.enums.AssessmentPhase;
import fit.iuh.engolearn.assessment.utils.AssessmentGrading;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AssessmentVersionTwoTest {
    private static final Instant START = Instant.parse("2026-11-05T00:00:00Z");

    private AssessmentAttempt attempt(int unit) {
        var a = new AssessmentAttempt();
        a.setPaper(AssessmentMapper.snapshot(AssessmentTestPaper.modernPaper()));
        a.setStartedAt(START);
        AssessmentTimeline.open(a, unit, START);
        return a;
    }

    @Test void singleHasOneThirtySecondDeadlineIncludingPreparationAndPlayback() {
        var a = attempt(0);
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.LISTENING_WAIT);
        assertThat(AssessmentTimeline.editable(a, 1)).isFalse();
        var waiting = AssessmentMapper.state(a, START, true);
        assertThat(waiting.deadline()).isEqualTo(START.plusSeconds(30));
        assertThat(waiting.audioStartsAt()).isEqualTo(START.plusSeconds(2));
        AssessmentTimeline.settle(a, START.plusSeconds(2));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.ANSWER);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(30));
        assertThat(AssessmentTimeline.editable(a, 1)).isTrue();
        assertThat(AssessmentTimeline.editable(a, 2)).isFalse();
        AssessmentTimeline.settle(a, START.plusSeconds(30).minusMillis(1));
        assertThat(a.getCursor()).isEqualTo(1);
        assertThat(AssessmentTimeline.editable(a, 1)).isTrue();
        AssessmentTimeline.settle(a, START.plusSeconds(30));
        assertThat(a.getCursor()).isEqualTo(2);
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.LISTENING_WAIT);
        assertThat(AssessmentTimeline.editable(a, 1)).isFalse();
    }

    @Test void groupHasOneFiveMinuteWindowAndArbitraryAnswerOrder() {
        var a = attempt(10);
        AssessmentTimeline.settle(a, START.plusSeconds(2));
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(300));
        for (int q : new int[]{15, 11, 14, 12, 13}) assertThat(AssessmentTimeline.editable(a, q)).isTrue();
        assertThat(AssessmentTimeline.editable(a, 10)).isFalse();
        assertThat(AssessmentTimeline.editable(a, 16)).isFalse();
        AssessmentTimeline.settle(a, START.plusSeconds(122));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.ANSWER);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(300));
        var state = AssessmentMapper.state(a, START.plusSeconds(122), true);
        assertThat(state.questions()).extracting(q -> q.number()).containsExactly(11, 12, 13, 14, 15);
        assertThat(state.audioStartsAt()).isEqualTo(START.plusSeconds(2));
        AssessmentTimeline.settle(a, START.plusSeconds(300));
        assertThat(a.getCursor()).isEqualTo(16);
        assertThat(AssessmentTimeline.editable(a, 15)).isFalse();
    }

    @Test void earlyGroupFinishStartsFreshWaitAndDoesNotCarryUnusedTime() {
        var a = attempt(10);
        AssessmentTimeline.settle(a, START.plusSeconds(2));
        AssessmentTimeline.finishStage(a, START.plusSeconds(10));
        assertThat(a.getCursor()).isEqualTo(16);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(12));
        AssessmentTimeline.settle(a, START.plusSeconds(12));
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(310));
    }

    @Test void readingExposesThirtyQuestionsAndOneDeadlineWithoutPreview() {
        var a = attempt(12);
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.ANSWER);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(1800));
        for (int q : new int[]{50, 21, 40, 30}) assertThat(AssessmentTimeline.editable(a, q)).isTrue();
        assertThat(AssessmentTimeline.editable(a, 20)).isFalse();
        assertThatThrownBy(() -> AssessmentTimeline.next(a, START)).isInstanceOf(IllegalArgumentException.class);
        var state = AssessmentMapper.state(a, START, true);
        assertThat(state.questions()).hasSize(30);
        assertThat(state.questions().get(10).passage()).isNotBlank();
        assertThat(state.audioAvailable()).isFalse();
        AssessmentTimeline.settle(a, START.plusSeconds(1800));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.FINISHED);
    }

    @Test void delayedWorkerFinishesAt2700SecondsWithoutGrantingExtraTime() {
        var a = attempt(0);
        AssessmentTimeline.settle(a, START.plusSeconds(4000));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.FINISHED);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(2700));
    }

    @Test void gradingAndReviewUseSnapshotSectionAfterRenumbering() {
        var a = attempt(0);
        a.getAnswers().put("1", 1);
        a.getAnswers().put("21", 1);
        a.getAnswers().put("50", 1);
        AssessmentGrading.grade(a, START.plusSeconds(2700));
        assertThat(a.getListeningCorrect()).isEqualTo(1);
        assertThat(a.getReadingCorrect()).isEqualTo(2);
        var review = AssessmentMapper.review(a, AssessmentMapper.result(a, null, true, true));
        assertThat(review.questions().getFirst().section()).isEqualTo("LISTENING");
        assertThat(review.questions().get(20).section()).isEqualTo("READING");
        assertThat(a.getPaper().getRulesVersion()).isEqualTo(2);
    }
}
