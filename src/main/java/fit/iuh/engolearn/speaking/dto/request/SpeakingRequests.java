package fit.iuh.engolearn.speaking.dto.request;

import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public final class SpeakingRequests {
    private SpeakingRequests() {}

    public record Create(@NotNull SpeakingScopeDto scope, @NotNull UUID clientRequestId) {}
    public record Replace(@NotNull SpeakingScopeDto scope, @NotNull UUID clientRequestId,
                          @NotBlank String sessionId, @NotNull @Positive Long controllerEpoch) {}
    public record Resume(@NotNull UUID clientRequestId, @NotNull @Positive Long controllerEpoch) {}
    public record Control(@NotNull @Positive Long controllerEpoch) {}
}
