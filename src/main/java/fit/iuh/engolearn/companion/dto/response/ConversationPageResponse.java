package fit.iuh.engolearn.companion.dto.response;

import java.util.List;

public record ConversationPageResponse(List<ConversationResponse> items, String nextCursor) {
    public ConversationPageResponse {
        items = List.copyOf(items);
    }
}
