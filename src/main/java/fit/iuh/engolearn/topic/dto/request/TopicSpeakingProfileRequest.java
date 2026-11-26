package fit.iuh.engolearn.topic.dto.request;

import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingProfileDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TopicSpeakingProfileRequest(@NotNull @PositiveOrZero Long revision,
                                          @NotNull TopicSpeakingProfileDto profile) {
    public record Publish(@NotNull @PositiveOrZero Long revision) {}
}
