package fit.iuh.engolearn.companion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompanionTurnRequest(
        @NotNull UUID turnId,
        @NotBlank String content,
        boolean retry) {
}
