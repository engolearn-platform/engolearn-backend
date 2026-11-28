package fit.iuh.engolearn.companion.service;

import fit.iuh.engolearn.companion.dto.response.ConversationPageResponse;
import fit.iuh.engolearn.companion.dto.response.ConversationResponse;
import fit.iuh.engolearn.companion.dto.response.MessagePageResponse;

public interface CompanionConversationService {
    ConversationResponse create();
    ConversationPageResponse list(String before, int limit);
    ConversationResponse get(String conversationId);
    void delete(String conversationId);
    MessagePageResponse messages(String conversationId, Long beforeSequence, int limit);
}
