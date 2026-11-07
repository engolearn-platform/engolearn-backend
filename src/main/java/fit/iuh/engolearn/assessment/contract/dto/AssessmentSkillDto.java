package fit.iuh.engolearn.assessment.contract.dto;

import fit.iuh.engolearn.assessment.contract.enums.AssessmentSkillBand;

public record AssessmentSkillDto(String code,int correct,int total,int percentage,AssessmentSkillBand band) {}
