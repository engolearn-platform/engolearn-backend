package fit.iuh.engolearn.topic.dto.request;

import fit.iuh.engolearn.topic.model.enums.LearningStage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdvanceTopicContentRequest(@NotNull @Min(0) Long revision, @NotNull LearningStage stage, @NotNull @Min(0) Integer itemIndex) {}
