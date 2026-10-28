package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CompleteTopicAttemptRequest(@NotNull @PositiveOrZero Long revision) {}
