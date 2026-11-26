package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface UserTopicItemAttemptRepository extends MongoRepository<UserTopicItemAttempt, String> {
    List<UserTopicItemAttempt> findByUserId(String userId);

    List<UserTopicItemAttempt> findByUserIdAndTopicIdIn(String userId, Collection<String> topicIds);

    Optional<UserTopicItemAttempt> findByUserTopicAttemptIdAndTopicItemId(String userTopicAttemptId,
                                                                           String topicItemId);

    List<UserTopicItemAttempt> findByUserIdAndTopicItemId(String userId, String topicItemId);

    List<UserTopicItemAttempt> findByUserIdAndTopicId(String userId, String topicId);

    boolean existsByTopicItemId(String topicItemId);
}
