package fit.iuh.engolearn.companion.dto.response;

import java.util.UUID;

public record CompanionTurnCompletedResponse(
        UUID turnId,
        MessageResponse assistantMessage,
        int messageCount,
        int maxMessages,
        boolean warning) {
}
