package fit.iuh.engolearn.learningpath.mapper;

import fit.iuh.engolearn.assessment.contract.dto.AssessmentResultDto;
import fit.iuh.engolearn.learningpath.model.PeriodicTestSnapshot;
import java.util.Date;
import java.util.Map;

public final class LearningPathAssessmentMapper {
    private static final Map<String, String> SKILLS = Map.of(
            "R01", "Reading for Specific Information",
            "R02", "Reading for Gist",
            "R04", "Inference & Author's View",
            "L01", "Listening for Gist",
            "L03", "Listening for Specific Information");

    private LearningPathAssessmentMapper() {}

    public static PeriodicTestSnapshot from(AssessmentResultDto result, String userId) {
        return new PeriodicTestSnapshot(result.attemptId(), userId, result.level(), result.paperId(),
                result.submittedAt() == null ? null : Date.from(result.submittedAt()),
                result.skills().stream().map(skill -> {
                    String name = SKILLS.get(skill.code());
                    if (name == null) throw new IllegalArgumentException("Unsupported Assessment skill: " + skill.code());
                    return new PeriodicTestSnapshot.MicroSkillResult(skill.code(), name,
                            skill.code().startsWith("L") ? "LISTENING" : "READING",
                            skill.correct(), skill.total(), skill.percentage(),
                            skill.band() == null ? null : skill.band().name(), true);
                }).toList());
    }
}
