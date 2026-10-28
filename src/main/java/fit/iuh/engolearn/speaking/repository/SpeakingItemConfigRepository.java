package fit.iuh.engolearn.speaking.repository;

import fit.iuh.engolearn.speaking.model.SpeakingItemConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SpeakingItemConfigRepository extends MongoRepository<SpeakingItemConfig, String> {
    Optional<SpeakingItemConfig> findByTopicItemVersionId(String topicItemVersionId);
}
