package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SpeakingTurnRepository extends MongoRepository<SpeakingTurn, String> {
    List<SpeakingTurn> findBySessionIdOrderBySequenceAsc(String sessionId);
    Optional<SpeakingTurn> findByIdAndSessionId(String id, String sessionId);
}
