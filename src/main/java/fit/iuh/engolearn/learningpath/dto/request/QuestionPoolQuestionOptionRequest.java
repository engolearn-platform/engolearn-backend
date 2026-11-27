package fit.iuh.engolearn.learningpath.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuestionPoolQuestionOptionRequest(
        @NotBlank @Pattern(regexp = "[A-D]") String optionId,
        @NotBlank @Size(max = 10000) String text) {
}
