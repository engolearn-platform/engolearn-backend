package fit.iuh.engolearn.assessment.dto.request;

import jakarta.validation.constraints.*;
public record AssessmentAudioRequest(@Min(0) @Max(25) int unitIndex,
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String issueId) {}
