package fit.iuh.engolearn.assessment.model;
import fit.iuh.engolearn.assessment.model.enums.*;
import fit.iuh.engolearn.assessment.model.vo.*;

import fit.iuh.engolearn.assessment.model.AssessmentAttempt;
import fit.iuh.engolearn.assessment.model.vo.AssessmentUnitVo;
import java.time.Instant;

public final class AssessmentTimeline {
    private static final int LISTENING_PREPARATION_SECONDS = 2;
    private static final int LISTENING_SINGLE_SECONDS = 30;
    private static final int LISTENING_GROUP_SECONDS = 300;

    private AssessmentTimeline() {}
    public static void open(AssessmentAttempt a, int index, Instant at) {
        a.setUnitIndex(index);
        if (index >= a.getPaper().getUnits().size()) {
            a.setPhase(AssessmentPhase.FINISHED); a.setNextDueAt(at); return;
        }
        AssessmentUnitVo u = unit(a);
        a.setCursor(u.getQuestions().getFirst().getNumber());
        a.setUnitStartedAt(at);
        if (modern(a)) {
            boolean listening = u.getKind().name().startsWith("LISTENING");
            a.setPhase(listening ? AssessmentPhase.LISTENING_WAIT : AssessmentPhase.ANSWER);
            a.setNextDueAt(listening ? audioStartsAt(a) : at.plusSeconds(1800));
            return;
        }
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
    public static boolean modern(AssessmentAttempt a) { return a.getPaper().getRulesVersion() == 2; }
    public static boolean reading(AssessmentAttempt a) { return unit(a).getKind().name().startsWith("READING"); }

    public static Instant audioStartsAt(AssessmentAttempt a) {
        return a.getUnitStartedAt().plusSeconds(LISTENING_PREPARATION_SECONDS);
    }

    public static Instant deadline(AssessmentAttempt a) {
        if (a.getPhase() == AssessmentPhase.LISTENING_WAIT) {
            int seconds = unit(a).getKind() == AssessmentUnitKind.LISTENING_SINGLE
                    ? LISTENING_SINGLE_SECONDS : LISTENING_GROUP_SECONDS;
            return a.getUnitStartedAt().plusSeconds(seconds);
        }
        return a.getNextDueAt();
    }

    public static int listeningSingleAudioLimitSeconds() {
        return LISTENING_SINGLE_SECONDS - LISTENING_PREPARATION_SECONDS;
    }

    public static boolean editable(AssessmentAttempt a, int question) {
        if (AssessmentPhase.READING_PREVIEW.equals(a.getPhase()) || AssessmentPhase.LISTENING_WAIT.equals(a.getPhase())) return false;
        if (!modern(a)) return question == a.getCursor() && question <= last(a);
        if (reading(a)) return question >= 21 && question <= 50;
        return unit(a).getQuestions().stream().anyMatch(q -> q.getNumber() == question);
    }
    public static void settle(AssessmentAttempt a, Instant now) {
        if (modern(a)) {
            while (!AssessmentPhase.FINISHED.equals(a.getPhase()) && !now.isBefore(a.getNextDueAt())) {
                Instant due = a.getNextDueAt();
                if (AssessmentPhase.LISTENING_WAIT.equals(a.getPhase())) {
                    a.setNextDueAt(deadline(a));
                    a.setPhase(AssessmentPhase.ANSWER);
                } else {
                    open(a, reading(a) ? a.getPaper().getUnits().size() : a.getUnitIndex() + 1, due);
                }
            }
            return;
        }
        if (AssessmentPhase.LISTENING_LEAD.equals(a.getPhase()) && a.getCursor()>last(a))
            open(a,a.getUnitIndex()+1,now.isBefore(a.getNextDueAt())?now:a.getNextDueAt());
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
        if (modern(a)) {
            if (unit(a).getKind() != AssessmentUnitKind.LISTENING_SINGLE || a.getPhase() != AssessmentPhase.ANSWER)
                throw new IllegalArgumentException("Only an open listening single can advance");
            open(a, a.getUnitIndex() + 1, now);
            return;
        }
        a.setCursor(a.getCursor()+1);
        if (a.getCursor()>last(a)) open(a,a.getUnitIndex()+1,now);
    }
    public static void finishStage(AssessmentAttempt a, Instant now) {
        if (modern(a)) {
            if (unit(a).getKind() != AssessmentUnitKind.LISTENING_PASSAGE || a.getPhase() != AssessmentPhase.ANSWER)
                throw new IllegalArgumentException("Only an open listening group can finish");
            open(a, a.getUnitIndex() + 1, now);
            return;
        }
        switch (a.getPhase()) {
            case READING_PREVIEW -> { a.setPhase(AssessmentPhase.ANSWER); a.setNextDueAt(now.plusSeconds(300)); }
            default -> throw new IllegalArgumentException("No reading preview to finish");
        }
    }
    public static AssessmentUnitVo unit(AssessmentAttempt a) { return a.getPaper().getUnits().get(a.getUnitIndex()); }
    public static int last(AssessmentAttempt a) { return unit(a).getQuestions().getLast().getNumber(); }
}
