package fit.iuh.engolearn.assessment;
import fit.iuh.engolearn.assessment.utils.*;

import fit.iuh.engolearn.assessment.model.enums.*;
import fit.iuh.engolearn.assessment.model.vo.*;

import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.service.impl.*;
import fit.iuh.engolearn.assessment.mapper.AssessmentMapper;
import fit.iuh.engolearn.assessment.exception.AssessmentException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class AssessmentRulesTest {
    private static final Instant START=Instant.parse("2026-10-30T00:00:00Z");
    private AssessmentAttempt attempt() {
        var a=new AssessmentAttempt();a.setPaper(AssessmentMapper.snapshot(AssessmentTestPaper.paper()));a.setStartedAt(START);
        AssessmentTimeline.open(a,0,START);return a;
    }
    @Test void exactDeadlineLocksSingleAndUsesActualDeadlineForNextQuestion() {
        var a=attempt();AssessmentTimeline.settle(a,START.plusSeconds(60).minusNanos(1));assertThat(a.getCursor()).isEqualTo(1);
        AssessmentTimeline.settle(a,START.plusSeconds(60));assertThat(a.getCursor()).isEqualTo(2);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(120));
    }
    @Test void lateWorkerDoesNotGrantAdditionalTimeAndSubmitsAt3600NotWorkerTime() {
        var a=attempt();AssessmentTimeline.settle(a,START.plusSeconds(4000));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.FINISHED);assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(3600));
        AssessmentGrading.grade(a,a.getNextDueAt());assertThat(a.getSubmittedAt()).isEqualTo(START.plusSeconds(3600));
    }
    @Test void earlySingleListeningNextDoesNotWaitForAudioOrTransferUnusedSeconds() {
        var a=attempt();AssessmentTimeline.open(a,14,START);
        AssessmentTimeline.next(a,START.plusSeconds(15));
        assertThat(a.getCursor()).isEqualTo(32);assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(75));
    }
    @Test void readingPreviewOpensShared300SecondWindowAtExactLeadDeadline() {
        var a=attempt();AssessmentTimeline.open(a,10,START);
        AssessmentTimeline.settle(a,START.plusSeconds(89));assertThat(a.getPhase()).isEqualTo(AssessmentPhase.READING_PREVIEW);
        AssessmentTimeline.settle(a,START.plusSeconds(90));assertThat(a.getPhase()).isEqualTo(AssessmentPhase.ANSWER);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(390));
        AssessmentTimeline.next(a,START.plusSeconds(100));assertThat(a.getCursor()).isEqualTo(12);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(390));
    }
    @Test void fastReadingFinishStartsListeningImmediatelyWithFreshSixtySeconds() {
        var a=attempt();AssessmentTimeline.open(a,13,START);a.setPhase(AssessmentPhase.ANSWER);a.setCursor(30);
        AssessmentTimeline.next(a,START.plusSeconds(250));assertThat(a.getCursor()).isEqualTo(31);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(310));
    }
    @ParameterizedTest @CsvSource({"10,11", "11,16", "12,21", "13,26"})
    void readingPreviewCanEndEarlyWithFreshSharedWindow(int index,int firstQuestion) {
        var a=attempt();AssessmentTimeline.open(a,index,START);
        AssessmentTimeline.finishStage(a,START.plusSeconds(5));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.ANSWER);
        assertThat(a.getCursor()).isEqualTo(firstQuestion);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(305));
        AssessmentTimeline.next(a,START.plusSeconds(10));
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(305));
    }
    @Test void listeningLeadCannotBeManuallyEnded() {
        var a=attempt();AssessmentTimeline.open(a,24,START);
        assertThatThrownBy(()->AssessmentTimeline.finishStage(a,START.plusSeconds(5))).isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.LISTENING_LEAD);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(120));
    }
    @Test void listeningPassageAnsweredEarlyOpensQuestion46ImmediatelyWithFreshLead() {
        var a=attempt();AssessmentTimeline.open(a,24,START);
        for(int n=0;n<5;n++) {a.getAnswers().put(""+a.getCursor(),1);AssessmentTimeline.next(a,START.plusSeconds(15));}
        assertThat(a.getUnitIndex()).isEqualTo(25);assertThat(a.getCursor()).isEqualTo(46);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(135));
        var state=AssessmentMapper.state(a,START.plusSeconds(15),true);
        assertThat(state.current().number()).isEqualTo(46);
        assertThat(state.progress().stream().filter(c->c.status().equals("CURRENT"))).hasSize(1);
    }
    @Test void completedLegacyListeningWaitSettlesImmediatelyWithoutChangingAnswers() {
        var a=attempt();AssessmentTimeline.open(a,24,START);a.setCursor(46);a.getAnswers().put("45",1);
        AssessmentTimeline.settle(a,START.plusSeconds(15));
        assertThat(a.getUnitIndex()).isEqualTo(25);assertThat(a.getCursor()).isEqualTo(46);
        assertThat(a.getAnswers()).containsEntry("45",1);
    }
    @Test void finalListeningNextFinishesImmediatelyAtTheClickTime() {
        var a=attempt();AssessmentTimeline.open(a,25,START);a.setCursor(50);
        AssessmentTimeline.next(a,START.plusSeconds(15));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.FINISHED);
        assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(15));
    }
    @Test void incompleteListeningPassageGetsExactly180SecondsAfterLead() {
        var a=attempt();AssessmentTimeline.open(a,24,START);AssessmentTimeline.settle(a,START.plusSeconds(120));
        assertThat(a.getPhase()).isEqualTo(AssessmentPhase.LISTENING_ANSWER);assertThat(a.getNextDueAt()).isEqualTo(START.plusSeconds(300));
        AssessmentTimeline.settle(a,START.plusSeconds(300));assertThat(a.getUnitIndex()).isEqualTo(25);
    }
    @Test void noAnswersIsNotZeroScoreOrA1() {
        var a=attempt();AssessmentGrading.grade(a,START.plusSeconds(1));
        assertThat(a.getStatus()).isEqualTo(AssessmentStatus.NO_RESULT);assertThat(a.getLevel()).isNull();assertThat(a.getCorrectCount()).isNull();
    }
    @Test void savedButWrongAnswerIsValidZeroAndStoresOnlyFiveMeasuredSkills() {
        var a=attempt();a.getAnswers().put("1",0);AssessmentGrading.grade(a,START.plusSeconds(1));
        assertThat(a.getStatus()).isEqualTo(AssessmentStatus.READY);assertThat(a.getLevel()).isEqualTo("A1");
        assertThat(a.getCorrectCount()).isZero();assertThat(a.getSkillResults()).hasSize(5);
        assertThat(a.getSkillResults()).extracting(AssessmentSkillResultVo::getCode).containsExactly("L01","L03","R01","R02","R04");
    }
    @Test void blanksRemainInFiftyQuestionDenominatorAndFreeReceivesOnlyTwoSkills() {
        var a=attempt();a.getAnswers().put("1",1);AssessmentGrading.grade(a,START);
        assertThat(a.getCorrectCount()).isEqualTo(1);assertThat(a.getReadingCorrect()).isEqualTo(1);
        assertThat(AssessmentMapper.result(a,null,false,true).skills()).hasSize(2);
        assertThat(AssessmentMapper.result(a,null,true,true).skills()).hasSize(5);
    }
    @ParameterizedTest @CsvSource({"0,A1","25,A1","26,A2","50,A2","51,B1","75,B1","76,B2","100,B2"})
    void aggregateLevelBoundaries(int score,String expected) {assertThat(AssessmentGrading.level(score)).isEqualTo(expected);}
    @Test void highestLevelNeverDecreases() {
        assertThat(AssessmentGrading.highest("B2","A1")).isEqualTo("B2");
        assertThat(AssessmentGrading.highest("A2","B1")).isEqualTo("B1");
    }
    @Test void historyOfAbandonedOrNoAnswerAttemptsContainsNoPaperScoreOrLevel() {
        var a=attempt();a.setStatus(AssessmentStatus.ABANDONED);
        var item=AssessmentMapper.history(a);assertThat(item.series()).isNull();assertThat(item.score()).isNull();assertThat(item.level()).isNull();
    }
    @Test void takingDtoDoesNotIncludeAnswerKeyExplanationTranscriptOrSkillScores() {
        var a=attempt();var value=AssessmentMapper.state(a,START,true);
        assertThat(value.current().getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("number","prompt","options","selected","passage");
    }
    @Test void selectionExcludesThreeRecentSeriesBeforeChoosingUnmeasuredSkills() {
        var service=new AssessmentPaperSelectionImpl(null);
        List<AssessmentAttempt> history=new ArrayList<>();
        for(int i=1;i<=3;i++) {var a=attempt();a.setPaper(AssessmentMapper.snapshot(AssessmentTestPaper.paper("p"+i,"s"+i,"0".repeat(64))));a.setSubmittedAt(START.minusSeconds(i));history.add(a);}
        var outside=AssessmentTestPaper.paper("p4","s4","0".repeat(64));
        assertThat(service.choose(List.of(AssessmentTestPaper.paper("p1","s1","0".repeat(64)),outside),history,START)).isEqualTo(outside);
    }
    @Test void fallbackChoosesOldestSeriesWhenAllAreRecent() {
        var service=new AssessmentPaperSelectionImpl(null);
        var a=attempt();a.setPaper(AssessmentMapper.snapshot(AssessmentTestPaper.paper("p1","s1","0".repeat(64))));a.setSubmittedAt(START.minusSeconds(10));
        var b=attempt();b.setPaper(AssessmentMapper.snapshot(AssessmentTestPaper.paper("p2","s2","0".repeat(64))));b.setSubmittedAt(START.minusSeconds(20));
        assertThat(service.choose(List.of(AssessmentTestPaper.paper("p1","s1","0".repeat(64)),AssessmentTestPaper.paper("p2","s2","0".repeat(64))),List.of(a,b),START).getId()).isEqualTo("p2");
    }
    @Test void noPublishedPaperFailsBeforeUsage() {
        assertThatThrownBy(()->new AssessmentPaperSelectionImpl(null).choose(List.of(),List.of(),START)).isInstanceOf(AssessmentException.class);
    }
    @Test void attemptSnapshotDoesNotShareMutableQuestionObjectsWithPublishedPaper() {
        var paper=AssessmentTestPaper.paper();var snapshot=AssessmentMapper.snapshot(paper);
        paper.getUnits().getFirst().getQuestions().getFirst().setPrompt("Replaced after start");
        assertThat(snapshot.getUnits().getFirst().getQuestions().getFirst().getPrompt()).isEqualTo("Question 1");
    }
}
