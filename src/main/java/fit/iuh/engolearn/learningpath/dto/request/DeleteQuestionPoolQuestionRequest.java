package fit.iuh.engolearn.learningpath.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DeleteQuestionPoolQuestionRequest(@NotNull @Min(1) Integer revision) {
}
