package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.UserTopicProgress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

public interface UserTopicProgressRepository extends MongoRepository<UserTopicProgress, String> {
    List<UserTopicProgress> findByUserId(String userId);

    List<UserTopicProgress> findByUserIdAndTopicIdIn(String userId, Collection<String> topicIds);

    Optional<UserTopicProgress> findByUserIdAndTopicId(String userId, String topicId);
}
