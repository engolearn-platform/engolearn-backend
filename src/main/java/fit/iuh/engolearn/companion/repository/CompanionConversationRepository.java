package fit.iuh.engolearn.companion.repository;

import fit.iuh.engolearn.companion.model.CompanionConversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CompanionConversationRepository {
    CompanionConversation insert(CompanionConversation conversation);
    Optional<CompanionConversation> findOwned(String id, String learnerId);
    List<CompanionConversation> listOwned(String learnerId, Instant before, String beforeId, int limit);
    Optional<CompanionConversation> admitNew(String id, String learnerId, String turnId, long expectedSequence, Instant now,
            Instant leaseUntil, int maxMessages);
    Optional<CompanionConversation> claimRetry(String id, String learnerId, String turnId, Instant now,
            Instant leaseUntil, int maxMessages);
    boolean finish(String id, String learnerId, String turnId, long generation, long expectedSequence, Instant now);
    boolean release(String id, String learnerId, String turnId, long generation, Instant now);
    boolean fenceActiveTurn(String id, String learnerId, String turnId, long generation, Instant now, Instant leaseUntil);
    boolean deleteOwned(String id, String learnerId);
}
