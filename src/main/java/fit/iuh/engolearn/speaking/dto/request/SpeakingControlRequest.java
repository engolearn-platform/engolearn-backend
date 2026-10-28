package fit.iuh.engolearn.speaking.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SpeakingControlRequest(long controllerEpoch, @NotBlank String requestId) {}
