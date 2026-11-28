package fit.iuh.engolearn.companion.dto.response;

import java.util.UUID;

public record FirstTurnStatusResponse(UUID turnId, State state, ConversationResponse conversation,
        MessageResponse userMessage, MessageResponse assistantMessage, boolean retryEligible) {
    public enum State { ACTIVE, COMPLETED, INCOMPLETE, CANCELLED, DELETED }
}
