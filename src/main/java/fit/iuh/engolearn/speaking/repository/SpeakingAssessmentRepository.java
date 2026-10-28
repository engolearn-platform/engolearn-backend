package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingAssessmentRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface SpeakingAssessmentRepository extends MongoRepository<SpeakingAssessmentRecord, String> {
    Optional<SpeakingAssessmentRecord> findById(String turnId);
}
