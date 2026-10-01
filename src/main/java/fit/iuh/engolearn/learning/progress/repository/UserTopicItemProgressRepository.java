package fit.iuh.engolearn.learning.progress.repository;

import fit.iuh.engolearn.learning.progress.model.ItemProgressStatus;
import fit.iuh.engolearn.learning.progress.model.UserTopicItemProgress;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserTopicItemProgressRepository
        extends MongoRepository<UserTopicItemProgress, String> {

    List<UserTopicItemProgress> findByUserIdAndTopicItemIdIn(String userId, List<String> topicItemIds);

    long countByUserIdAndTopicItemIdInAndStatus(
            String userId, List<String> topicItemIds, ItemProgressStatus status);
}
