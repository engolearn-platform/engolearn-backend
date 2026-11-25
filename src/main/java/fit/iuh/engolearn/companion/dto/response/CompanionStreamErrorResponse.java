package fit.iuh.engolearn.companion.dto.response;

import java.util.UUID;

public record CompanionStreamErrorResponse(UUID turnId, String code, String detail, boolean retryable) {
}
