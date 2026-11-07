package fit.iuh.engolearn.assessment.dto.request;

import jakarta.validation.constraints.*;

public record AssessmentFinishStageRequest(@Min(10) @Max(13) int unitIndex,
        @NotBlank @Pattern(regexp="READING_PREVIEW|ANSWER") String phase,
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String commandId) {}
