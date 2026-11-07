package fit.iuh.engolearn.placement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SavePlacementAnswerRequest(
        @NotBlank String questionId,
        @Pattern(regexp = "\\S.*") String selectedOptionId) {
}
