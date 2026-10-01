package fit.iuh.engolearn.content.topic.repository;

import fit.iuh.engolearn.content.topic.model.TopicVersion;
import fit.iuh.engolearn.shared.model.ContentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicVersionRepository extends MongoRepository<TopicVersion, String> {

    Optional<TopicVersion> findByTopicIdAndVersion(String topicId, Integer version);

    List<TopicVersion> findByTopicIdAndStatus(String topicId, ContentStatus status);
}
