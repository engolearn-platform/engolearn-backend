package fit.iuh.engolearn.assessment.dto.request;

import jakarta.validation.constraints.*;
public record AssessmentConfirmRequest(@AssertTrue boolean confirmed) {}
