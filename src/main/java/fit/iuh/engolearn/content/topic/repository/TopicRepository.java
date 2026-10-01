package fit.iuh.engolearn.content.topic.repository;

import fit.iuh.engolearn.content.topic.model.Topic;
import fit.iuh.engolearn.shared.model.CefrLevel;
import fit.iuh.engolearn.content.topic.model.TopicCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicRepository extends MongoRepository<Topic, String> {

    Page<Topic> findByCefrLevel(CefrLevel level, Pageable pageable);

    Page<Topic> findByCategory(TopicCategory category, Pageable pageable);

    Page<Topic> findByCefrLevelAndCategory(CefrLevel level, TopicCategory category, Pageable pageable);
}
