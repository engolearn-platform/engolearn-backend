package fit.iuh.engolearn.assessment.contract.dto;

import fit.iuh.engolearn.assessment.contract.enums.AssessmentCompanionScope;
import java.time.Instant;
import java.util.List;

public record AssessmentCompanionSummaryDto(String attemptId, Instant submittedAt, Integer officialScore,
        String level, AssessmentCompanionScope scope, List<AssessmentCompanionSkillDto> skills) {
    public AssessmentCompanionSummaryDto {
        skills = List.copyOf(skills);
    }
}
