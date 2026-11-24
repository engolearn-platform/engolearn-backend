package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TopicItemRepository extends MongoRepository<TopicItem, String> {
    List<TopicItem> findAllByTopicId(String topicId);

    Optional<TopicItem> findByIdAndTopicId(String id, String topicId);
}
