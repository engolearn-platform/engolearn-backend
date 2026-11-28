package fit.iuh.engolearn.companion.dto.response;

import java.util.List;

public record MessagePageResponse(List<MessageResponse> items, Long nextBeforeSequence) {
    public MessagePageResponse {
        items = List.copyOf(items);
    }
}
