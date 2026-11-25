package fit.iuh.engolearn.companion.dto.response;

import java.time.Instant;

public record ConversationResponse(
        String id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int messageCount,
        int maxMessages,
        int warningThreshold) {
}
