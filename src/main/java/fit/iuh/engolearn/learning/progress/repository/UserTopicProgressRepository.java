package fit.iuh.engolearn.learning.progress.repository;

import fit.iuh.engolearn.learning.progress.model.UserTopicProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserTopicProgressRepository extends MongoRepository<UserTopicProgress, String> {

    Optional<UserTopicProgress> findByUserIdAndTopicId(String userId, String topicId);

    List<UserTopicProgress> findByUserIdAndTopicIdIn(String userId, List<String> topicIds);
}
