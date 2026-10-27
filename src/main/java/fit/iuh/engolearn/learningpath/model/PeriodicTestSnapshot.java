package fit.iuh.engolearn.learningpath.model;

import org.bson.types.ObjectId;

import java.util.Date;
import java.util.List;

public record PeriodicTestSnapshot(
        ObjectId attemptId,
        ObjectId userId,
        String level,
        ObjectId testSetId,
        Date takenAt,
        List<MicroSkillResult> results
) {
    public record MicroSkillResult(
            ObjectId resultId,
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
