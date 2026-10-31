package fit.iuh.engolearn.assessment.utils;
import fit.iuh.engolearn.assessment.model.enums.*;
import fit.iuh.engolearn.assessment.model.vo.*;

import fit.iuh.engolearn.assessment.model.AssessmentAttempt;
import fit.iuh.engolearn.assessment.model.vo.AssessmentUnitVo;
import java.time.Instant;

public final class AssessmentTimeline {
    private AssessmentTimeline() {}
    public static void open(AssessmentAttempt a, int index, Instant at) {
        a.setUnitIndex(index);
        if (index >= a.getPaper().getUnits().size()) {
            a.setPhase(AssessmentPhase.FINISHED); a.setNextDueAt(at); return;
        }
        AssessmentUnitVo u = unit(a);
        a.setCursor(u.getQuestions().getFirst().getNumber());
        a.setUnitStartedAt(at);
        switch (u.getKind()) {
            case READING_SINGLE, LISTENING_SINGLE -> { a.setPhase(AssessmentPhase.ANSWER); a.setNextDueAt(at.plusSeconds(60)); }
            case READING_PASSAGE -> {
                int passageIndex=index-10;
                int lead=new int[]{90,120,150,240}[passageIndex];
                a.setPhase(AssessmentPhase.READING_PREVIEW); a.setNextDueAt(at.plusSeconds(lead));
            }
            case LISTENING_PASSAGE -> { a.setPhase(AssessmentPhase.LISTENING_LEAD); a.setNextDueAt(at.plusSeconds(120)); }
            default -> throw new IllegalArgumentException("Unknown unit kind");
        }
    }
    public static void settle(AssessmentAttempt a, Instant now) {
        while (!AssessmentPhase.FINISHED.equals(a.getPhase()) && !now.isBefore(a.getNextDueAt())) {
            Instant due=a.getNextDueAt();
            switch (a.getPhase()) {
                case READING_PREVIEW -> { a.setPhase(AssessmentPhase.ANSWER); a.setNextDueAt(due.plusSeconds(300)); }
                case LISTENING_LEAD -> {
                    if (a.getCursor()>last(a)) open(a,a.getUnitIndex()+1,due);
                    else { a.setPhase(AssessmentPhase.LISTENING_ANSWER); a.setNextDueAt(due.plusSeconds(180)); }
                }
                default -> open(a,a.getUnitIndex()+1,due);
            }
        }
    }
    public static void next(AssessmentAttempt a, Instant now) {
        a.setCursor(a.getCursor()+1);
        if (a.getCursor()>last(a) && !AssessmentPhase.LISTENING_LEAD.equals(a.getPhase())) open(a,a.getUnitIndex()+1,now);
    }
    public static AssessmentUnitVo unit(AssessmentAttempt a) { return a.getPaper().getUnits().get(a.getUnitIndex()); }
    public static int last(AssessmentAttempt a) { return unit(a).getQuestions().getLast().getNumber(); }
}
