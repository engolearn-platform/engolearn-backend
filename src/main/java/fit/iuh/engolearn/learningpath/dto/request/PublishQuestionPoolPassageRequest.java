package fit.iuh.engolearn.learningpath.dto.request;

import fit.iuh.engolearn.learningpath.model.enums.PassageReviewCheck;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record PublishQuestionPoolPassageRequest(
        @NotNull @Min(1) Integer revision,
        @NotNull @Size(max = 4) Set<@NotNull PassageReviewCheck> confirmedReviewChecks) {
    public PublishQuestionPoolPassageRequest {
        if (confirmedReviewChecks != null) confirmedReviewChecks = Set.copyOf(confirmedReviewChecks);
    }
}
