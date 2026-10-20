package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Persistence access to the published topic versions. Single reads use
 * {@code findById}; the list path resolves many releases at once with
 * {@code findAllById}.
 */
public interface TopicVersionRepository extends MongoRepository<TopicVersion, String> {
}