package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicRetryRuntime;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TopicRetryRuntimeRepository extends MongoRepository<TopicRetryRuntime,String> {
    List<TopicRetryRuntime> findByUserId(String userId);
}
