package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.UserTopicAttempt;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.AttemptType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Collection;

public interface UserTopicAttemptRepository extends MongoRepository<UserTopicAttempt, String> {
    List<UserTopicAttempt> findByUserIdAndTopicIdInAndIdIn(String userId, Collection<String> topicIds,
                                                         Collection<String> attemptIds);

    List<UserTopicAttempt> findByUserIdAndTopicIdAndAttemptTypeAndStatus(String userId, String topicId,
                                                                          AttemptType attemptType,
                                                                          AttemptStatus status);
}
