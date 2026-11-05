package fit.iuh.engolearn.assessment.mapper;
import fit.iuh.engolearn.assessment.model.enums.*;
import fit.iuh.engolearn.assessment.model.vo.*;

import fit.iuh.engolearn.assessment.model.*;
import fit.iuh.engolearn.assessment.dto.response.*;
import java.time.Instant;
import java.util.*;

public final class AssessmentMapper {
    private AssessmentMapper() {}
    public static AssessmentPaperSnapshotVo snapshot(AssessmentPaper paper) {
        var skills=paper.getSkills().stream().map(s->new AssessmentSkillVo(s.getCode(),s.getName(),s.getExample(),s.getSection())).toList();
        var units=paper.getUnits().stream().map(u->new AssessmentUnitVo(u.getKind(),u.getPassage(),u.getAudioPath(),u.getAudioSha256(),
                u.getQuestions().stream().map(q->new AssessmentQuestionVo(q.getNumber(),q.getLevel(),q.getSkillCode(),q.getPrompt(),
                        List.copyOf(q.getOptions()),q.getCorrectOption(),q.getExplanation(),q.getTranscript())).toList())).toList();
        return new AssessmentPaperSnapshotVo(paper.getId(),paper.getSeries(),paper.getVersion(),paper.getRulesVersion(),paper.isDemonstration(),skills,units);
    }
    public static AssessmentStateResponse state(AssessmentAttempt a, Instant now, boolean dev) {
        int rules = a.getPaper().getRulesVersion();
        if (a.getStatus() != AssessmentStatus.ACTIVE) return new AssessmentStateResponse(
                a.getId(), a.getStatus().name(), a.getVersion() == null ? 0 : a.getVersion(), now,
                a.getStartedAt(), null, "FINISHED", a.getUnitIndex(), null, a.getPaper().getSeries(),
                a.getPaper().getVersion(), null, null, List.of(), false, List.of(), a.getAnswers().size(),
                dev, rules, null, List.of());
        var unit = a.getPaper().getUnits().get(a.getUnitIndex());
        boolean reading = unit.getKind().name().startsWith("READING");
        boolean preview = a.getPhase() == AssessmentPhase.READING_PREVIEW;
        var current = unit.getQuestions().stream().filter(q -> q.getNumber() == a.getCursor()).findFirst();
        List<AssessmentQuestionResponse> questions = new ArrayList<>();
        if (rules == 2) {
            var visibleUnits = reading ? a.getPaper().getUnits().subList(12, 26) : List.of(unit);
            for (var u : visibleUnits) for (var q : u.getQuestions()) questions.add(question(q, a, u.getPassage()));
        }
        List<AssessmentCellResponse> cells = new ArrayList<>();
        for (var u : a.getPaper().getUnits()) {
            if (u.getKind().name().startsWith("READING") != reading) continue;
            for (var q : u.getQuestions()) {
                int n = q.getNumber();
                String status = a.getAnswers().containsKey("" + n) ? "ANSWERED"
                        : n < a.getCursor() ? "BLANK" : "FUTURE";
                if (n == a.getCursor() && current.isPresent()) status = "CURRENT";
                cells.add(new AssessmentCellResponse(n, status));
            }
        }
        boolean audio = !reading && (a.getPhase() == AssessmentPhase.ANSWER
                || a.getPhase() == AssessmentPhase.LISTENING_LEAD || a.getPhase() == AssessmentPhase.LISTENING_WAIT);
        return new AssessmentStateResponse(a.getId(), a.getStatus().name(), a.getVersion() == null ? 0 : a.getVersion(),
                now, a.getStartedAt(), AssessmentTimeline.deadline(a), a.getPhase().name(), a.getUnitIndex(),
                reading ? "READING" : "LISTENING", a.getPaper().getSeries(), a.getPaper().getVersion(),
                unit.getPassage(), preview ? null : current.map(q -> question(q, a, unit.getPassage())).orElse(null),
                preview ? unit.getQuestions().stream().map(q -> question(q, a, unit.getPassage())).toList() : List.of(),
                audio, cells, a.getAnswers().size(), dev, rules,
                rules == 2 && !reading ? AssessmentTimeline.audioStartsAt(a) : null, questions);
    }
    private static AssessmentQuestionResponse question(AssessmentQuestionVo q, AssessmentAttempt a, String passage) {
        return new AssessmentQuestionResponse(q.getNumber(), q.getPrompt(), q.getOptions(), a.getAnswers().get("" + q.getNumber()), passage);
    }
    public static AssessmentHistoryItemResponse history(AssessmentAttempt a) {
        boolean scored=AssessmentStatus.READY.equals(a.getStatus());
        return new AssessmentHistoryItemResponse(a.getId(),a.getStatus().name(),a.getSubmittedAt(),scored?a.getPaper().getSeries():null,
                scored?a.getPaper().getVersion():null,scored?a.getCorrectCount()*2:null,scored?a.getLevel():null);
    }
    public static AssessmentResultResponse result(AssessmentAttempt a,AssessmentProfile p,boolean full,boolean dev) {
        List<AssessmentSkillResponse> skills=a.getSkillResults().stream().limit(full?5:2).map(s->new AssessmentSkillResponse(s.getCode(),s.getName(),s.getExample(),
                s.getSection().name(),s.getCorrect(),s.getTotal(),s.getPercentage(),s.getBand().name())).toList();
        return new AssessmentResultResponse(a.getId(),a.getStatus().name(),a.getSubmittedAt(),a.getPaper().getSeries(),a.getPaper().getVersion(),a.getCorrectCount(),
                a.getCorrectCount()==null?null:a.getCorrectCount()*2,a.getReadingCorrect(),a.getListeningCorrect(),a.getLevel(),
                p==null?null:p.getInitialLevel(),p==null?null:p.getCurrentLevel(),p==null?null:p.getHighestLevel(),skills,
                a.getSkillResults().size(),full,dev);
    }
    public static AssessmentReviewResponse review(AssessmentAttempt a,AssessmentResultResponse result) {
        List<AssessmentReviewQuestionResponse> questions=new ArrayList<>();
        for(int index=0;index<a.getPaper().getUnits().size();index++) {
            var u=a.getPaper().getUnits().get(index);
            for(var q:u.getQuestions()) questions.add(new AssessmentReviewQuestionResponse(q.getNumber(),q.getPrompt(),q.getOptions(),
                    a.getAnswers().get(""+q.getNumber()),q.getCorrectOption(),q.getExplanation(),q.getTranscript(),
                    u.getKind().name().startsWith("READING")?"READING":"LISTENING",u.getPassage(),index,u.getAudioPath()!=null));
        }
        return new AssessmentReviewResponse(result,questions);
    }
}
