package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicItem;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for the {@code topic_items} collection (design record F05 §3.2).
 */
public interface TopicItemRepository extends MongoRepository<TopicItem, String> {
}
