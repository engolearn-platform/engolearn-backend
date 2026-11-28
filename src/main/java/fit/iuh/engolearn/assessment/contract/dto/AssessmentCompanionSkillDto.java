package fit.iuh.engolearn.assessment.contract.dto;

import fit.iuh.engolearn.assessment.contract.enums.AssessmentSkillBand;

public record AssessmentCompanionSkillDto(String code, String name, String example, String section,
        int correct, int total, int percentage, AssessmentSkillBand band) {}
