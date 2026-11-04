package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.user.model.RefreshSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RefreshSessionRepository extends MongoRepository<RefreshSession, String>, RefreshSessionRepositoryCustom {
    Optional<RefreshSession> findBySessionId(String sessionId);
}
