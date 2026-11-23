package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SpeakingSessionRepository extends MongoRepository<SpeakingSession, String> {
    Optional<SpeakingSession> findFirstByLearnerIdAndStatusInAndDeletedAtIsNull(
            String learnerId, List<SpeakingSession.Status> statuses);
    List<SpeakingSession> findByLearnerIdAndContentTopicItemIdAndDeletedAtIsNullOrderByStartedAtDesc(
            String learnerId, String itemId);
    List<SpeakingSession> findByLearnerIdAndDeletedAtIsNullOrderByStartedAtDesc(String learnerId);
    List<SpeakingSession> findByStatusIn(List<SpeakingSession.Status> statuses);
}
