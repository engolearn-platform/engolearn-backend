package fit.iuh.engolearn.topic.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TopicPublishRequest(@NotNull @Min(0) Long revision) {
}
