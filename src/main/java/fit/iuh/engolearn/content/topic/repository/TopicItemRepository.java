package fit.iuh.engolearn.content.topic.repository;

import fit.iuh.engolearn.content.topic.model.TopicItem;
import fit.iuh.engolearn.shared.model.ContentStatus;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicItemRepository extends MongoRepository<TopicItem, String> {

    List<TopicItem> findByTopicVersionIdAndStatusOrderByOrderAsc(String topicVersionId, ContentStatus status);

    List<TopicItem> findByTopicVersionId(String topicVersionId);
}
