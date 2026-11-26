package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface UserTopicItemProgressRepository extends MongoRepository<UserTopicItemProgress, String> {
    List<UserTopicItemProgress> findByUserId(String userId);

    List<UserTopicItemProgress> findByUserIdAndTopicIdIn(String userId, Collection<String> topicIds);

    Optional<UserTopicItemProgress> findByUserIdAndTopicItemId(String userId, String topicItemId);

    List<UserTopicItemProgress> findByUserIdAndTopicId(String userId, String topicId);

    boolean existsByTopicItemId(String topicItemId);
}
