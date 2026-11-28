package fit.iuh.engolearn.companion.dto.response;

import java.time.Instant;

public record MessageResponse(
        String id,
        long sequence,
        String role,
        String content,
        Instant createdAt) {
}
