package fit.iuh.engolearn.assessment.utils;
import fit.iuh.engolearn.assessment.model.enums.*;
import fit.iuh.engolearn.assessment.model.vo.*;

import fit.iuh.engolearn.assessment.model.*;
import java.time.Instant;
import java.util.*;

public final class AssessmentGrading {
    private AssessmentGrading() {}
    public static String level(int score) { return score<=25?"A1":score<=50?"A2":score<=75?"B1":"B2"; }
    public static String highest(String first, String second) {
        List<String> levels=List.of("A1","A2","B1","B2","C1","C2");
        return levels.indexOf(first)>=levels.indexOf(second)?first:second;
    }
    public static void grade(AssessmentAttempt a, Instant at) {
        a.setSubmittedAt(at);
        if(a.getAnswers().isEmpty()) { a.setStatus(AssessmentStatus.NO_RESULT); return; }
        Map<String,Integer> counts=new HashMap<>();
        int reading=0, listening=0;
        for(var u:a.getPaper().getUnits()) for(var q:u.getQuestions()) {
            boolean correct=Objects.equals(a.getAnswers().get(Integer.toString(q.getNumber())),q.getCorrectOption());
            if(correct) {
                counts.merge(q.getSkillCode(),1,Integer::sum);
                if(u.getKind().name().startsWith("READING")) reading++; else listening++;
            }
        }
        List<AssessmentSkillResultVo> skills=new ArrayList<>();
        for(var skill:a.getPaper().getSkills()) {
            int correct=counts.getOrDefault(skill.getCode(),0),percent=correct*10;
            skills.add(new AssessmentSkillResultVo(skill.getCode(),skill.getName(),skill.getExample(),skill.getSection(),
                    correct,10,percent,percent<50?AssessmentSkillBand.NEEDS_PRACTICE:percent<80?AssessmentSkillBand.MONITOR:AssessmentSkillBand.MEETS_THRESHOLD));
        }
        skills.sort(Comparator.comparingInt(AssessmentSkillResultVo::getPercentage).thenComparing(AssessmentSkillResultVo::getCode));
        a.setSkillResults(skills); a.setReadingCorrect(reading);a.setListeningCorrect(listening);
        a.setCorrectCount(reading+listening); a.setLevel(level((reading+listening)*2)); a.setStatus(AssessmentStatus.READY);
    }
}
