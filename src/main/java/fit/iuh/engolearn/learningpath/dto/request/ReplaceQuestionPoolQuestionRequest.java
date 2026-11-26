package fit.iuh.engolearn.learningpath.dto.request;

import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import jakarta.validation.constraints.*;
import java.util.Set;

public record ReplaceQuestionPoolQuestionRequest(
        @NotNull @Min(1) Integer revision,
        @NotBlank @Size(max = 100) String replacementQuestionId,
        @NotNull @Min(1) Integer replacementRevision,
        @NotNull @Size(max = 9) Set<@NotNull QuestionReviewCheck> confirmedReviewChecks,
        Boolean confirmCoverageWarning) {
    public ReplaceQuestionPoolQuestionRequest {
        confirmCoverageWarning = Boolean.TRUE.equals(confirmCoverageWarning);
    }
}
