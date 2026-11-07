package fit.iuh.engolearn.learningpath.model;

import java.util.Date;
import java.util.List;

public record PeriodicTestSnapshot(
        String attemptId,
        String userId,
        String level,
        String paperId,
        Date takenAt,
        List<MicroSkillResult> results
) {
    public record MicroSkillResult(
            String code,
            String microSkill,
            String skill,
            int correct,
            int total,
            double percent,
            String band,
            boolean valid
    ) {
    }
}
