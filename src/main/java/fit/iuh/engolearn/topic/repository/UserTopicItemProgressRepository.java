package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserTopicItemProgressRepository extends MongoRepository<UserTopicItemProgress, String> {
    List<UserTopicItemProgress> findByUserId(String userId);

    Optional<UserTopicItemProgress> findByUserIdAndTopicItemId(String userId, String topicItemId);

    List<UserTopicItemProgress> findByUserIdAndTopicId(String userId, String topicId);
}
