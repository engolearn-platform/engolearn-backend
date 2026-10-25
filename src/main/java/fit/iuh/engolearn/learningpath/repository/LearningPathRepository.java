
package fit.iuh.engolearn.learningpath.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import fit.iuh.engolearn.learningpath.model.LearningPath;

import java.util.List;
import java.util.Optional;

public interface LearningPathRepository extends MongoRepository<LearningPath, ObjectId> {

    List<LearningPath> findByUserIdAndStatusInOrderByCreatedAtDesc(
            ObjectId userId,
            List<String> statuses
    );

    long countByUserIdAndStatusIn(ObjectId userId, List<String> statuses);

    long countByUserIdAndStatus(ObjectId userId, String status);

    Optional<LearningPath> findByIdAndUserId(ObjectId id, ObjectId userId);
}
