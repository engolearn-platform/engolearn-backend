package fit.iuh.engolearn.assessment.dto.response;

import jakarta.validation.constraints.*;
public record AssessmentSkillResponse(String code,String name,String example,String section,int correct,int total,int percentage,String band) {}
