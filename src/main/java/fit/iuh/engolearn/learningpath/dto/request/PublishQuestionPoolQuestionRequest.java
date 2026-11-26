package fit.iuh.engolearn.learningpath.dto.request;

import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record PublishQuestionPoolQuestionRequest(
        @NotNull @Min(1) Integer revision,
        @NotNull @Size(max = 9) Set<@NotNull QuestionReviewCheck> confirmedReviewChecks) {
}
