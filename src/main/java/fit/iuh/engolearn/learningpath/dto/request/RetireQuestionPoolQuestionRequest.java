package fit.iuh.engolearn.learningpath.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RetireQuestionPoolQuestionRequest(@NotNull @Min(1) Integer revision, Boolean confirmCoverageWarning) {
    public RetireQuestionPoolQuestionRequest {
        confirmCoverageWarning = Boolean.TRUE.equals(confirmCoverageWarning);
    }
}
