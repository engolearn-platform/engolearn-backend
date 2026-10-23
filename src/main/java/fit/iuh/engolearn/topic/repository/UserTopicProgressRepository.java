package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.UserTopicProgress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserTopicProgressRepository extends MongoRepository<UserTopicProgress, String> {

    Optional<UserTopicProgress> findByUserIdAndTopicId(String userId, String topicId);
}
