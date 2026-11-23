package fit.iuh.engolearn.assessment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssessmentDraftRevisionRequest(@NotBlank @Size(max = 64) String revision) {}
