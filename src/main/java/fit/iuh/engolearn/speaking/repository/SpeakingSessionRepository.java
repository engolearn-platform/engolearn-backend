package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioStatus;
import java.time.Instant;
import fit.iuh.engolearn.topic.contract.enums.SpeakingScopeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpeakingSessionRepository extends MongoRepository<SpeakingSession, String> {
    Optional<SpeakingSession> findByOwnerIdAndClientRequestId(String ownerId, String requestId);
    Optional<SpeakingSession> findByUnfinishedOwner(String ownerId);
    List<SpeakingSession> findTop100ByOwnerIdAndDeletedAtIsNotNullAndActiveTurnSealedAtIsNotNull(String ownerId);
    List<SpeakingSession> findTop100ByDeletedAtIsNotNullAndActiveTurnSealedAtIsNotNull();
    boolean existsByOwnerIdAndDeletedAtIsNotNullAndActiveTurnSealedAtIsNotNull(String ownerId);
    Slice<SpeakingSession> findByOwnerIdAndDeletedAtIsNull(String ownerId, Pageable pageable);
    Slice<SpeakingSession> findByOwnerIdAndDeletedAtIsNullAndSnapshotScopeTypeAndSnapshotTopicIdAndSnapshotTopicItemId(
            String ownerId, SpeakingScopeType scopeType, String topicId, String itemId, Pageable pageable);
    List<SpeakingSession> findByOpeningAudioStatusAndOpeningDeadlineLessThanEqualAndDeletedAtIsNull(
            SpeakingAudioStatus audioStatus, Instant now);
    List<SpeakingSession> findByStatusInAndDeletedAtIsNull(Collection<SpeakingSessionStatus> statuses);
}
