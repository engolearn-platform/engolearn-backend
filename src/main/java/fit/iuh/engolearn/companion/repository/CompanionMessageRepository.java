package fit.iuh.engolearn.companion.repository;

import fit.iuh.engolearn.companion.model.CompanionMessage;
import java.util.List;
import java.util.Optional;

public interface CompanionMessageRepository {
    CompanionMessage insert(CompanionMessage message);
    Optional<CompanionMessage> findByTurnAndRole(String conversationId, String turnId, CompanionMessage.Role role);
    List<CompanionMessage> newest(String conversationId, Long beforeSequence, int limit);
    void deleteConversationMessages(String conversationId);
}
