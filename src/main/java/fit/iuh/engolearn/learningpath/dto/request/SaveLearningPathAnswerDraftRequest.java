package fit.iuh.engolearn.learningpath.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SaveLearningPathAnswerDraftRequest(
        @NotBlank @Pattern(regexp = "[ABCD]") String selectedOptionId
) {}
