
package fit.iuh.engolearn.learningpath.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import fit.iuh.engolearn.learningpath.model.LearningPath;

import java.util.List;
import java.util.Optional;

public interface LearningPathRepository extends MongoRepository<LearningPath, String> {

    List<LearningPath> findByUserIdAndStatusInOrderByCreatedAtDesc(
            String userId,
            List<String> statuses
    );

    List<LearningPath> findByUserIdAndStatusInAndMicroSkillsMicroSkillContainingIgnoreCaseOrderByCreatedAtDesc(
            String userId,
            List<String> statuses,
            String microSkill
    );

    List<LearningPath> findByUserIdAndStatusInAndCreatedDateOrderByCreatedAtDesc(
            String userId,
            List<String> statuses,
            String createdDate
    );

    long countByUserIdAndStatusIn(String userId, List<String> statuses);

    long countByUserIdAndStatus(String userId, String status);

    Optional<LearningPath> findByIdAndUserId(ObjectId id, String userId);

    Optional<LearningPath> findBySourceAttemptId(ObjectId attemptId);

    List<LearningPath> findByUserIdAndActiveTrue(String userId);
}
