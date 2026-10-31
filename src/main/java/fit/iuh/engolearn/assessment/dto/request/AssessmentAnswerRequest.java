package fit.iuh.engolearn.assessment.dto.request;

import jakarta.validation.constraints.*;
public record AssessmentAnswerRequest(@Min(1) @Max(50) int question,@Min(0) @Max(9_007_199_254_740_991L) long sequence,
            @Min(0) @Max(3) Integer choice,
            @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String commandId) {}
