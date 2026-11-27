package fit.iuh.engolearn.learningpath.mapper;

import fit.iuh.engolearn.assessment.contract.AssessmentSkillCatalog;
import fit.iuh.engolearn.assessment.contract.dto.AssessmentResultDto;
import fit.iuh.engolearn.learningpath.model.PeriodicTestSnapshot;
import java.util.Date;

public final class LearningPathAssessmentMapper {
    private LearningPathAssessmentMapper() {}

    public static PeriodicTestSnapshot from(AssessmentResultDto result, String userId) {
        return new PeriodicTestSnapshot(result.attemptId(), userId, result.level(), result.paperId(),
                result.submittedAt() == null ? null : Date.from(result.submittedAt()),
                result.skills().stream().map(skill -> {
                    var definition = AssessmentSkillCatalog.find(skill.code())
                            .orElseThrow(() -> new IllegalArgumentException("Unsupported Assessment skill: " + skill.code()));
                    return new PeriodicTestSnapshot.MicroSkillResult(skill.code(), definition.learningPathName(),
                            definition.section(),
                            skill.correct(), skill.total(), skill.percentage(),
                            skill.band() == null ? null : skill.band().name(), true);
                }).toList());
    }
}
