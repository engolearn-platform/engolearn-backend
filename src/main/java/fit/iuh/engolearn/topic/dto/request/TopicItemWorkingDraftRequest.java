package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TopicItemWorkingDraftRequest(@NotNull @Min(0) Long revision,
                                           @NotBlank String baseItemVersionId) {
}
