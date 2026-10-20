package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicItem;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicItemRepository extends MongoRepository<TopicItem, String> {
}
