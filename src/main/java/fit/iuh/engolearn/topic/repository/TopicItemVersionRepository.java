package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicItemVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicItemVersionRepository extends MongoRepository<TopicItemVersion, String>, TopicStatsCustom {
}
