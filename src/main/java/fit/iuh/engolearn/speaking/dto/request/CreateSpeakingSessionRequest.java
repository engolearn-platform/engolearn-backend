package fit.iuh.engolearn.speaking.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSpeakingSessionRequest(@NotBlank String itemId, @NotBlank String requestId) {}
