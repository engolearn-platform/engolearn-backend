package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.user.model.UserProfile;
import fit.iuh.engolearn.user.model.vo.UserAssessmentProgressVo;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserAssessmentProgressRepository {
    private final MongoTemplate mongo;

    public Optional<UserAssessmentProgressVo> find(String userId) {
        return Optional.ofNullable(mongo.findById(userId, UserProfile.class))
                .map(UserProfile::getAssessmentProgress);
    }

    public Optional<UserAssessmentProgressVo> findLegacy(String userId) {
        var query = Query.query(Criteria.where("user_id").is(userId));
        query.fields().include("initial_level", "current_level", "highest_level",
                "latest_valid_at", "latest_valid_attempt_id", "development_data");
        var legacy = mongo.findOne(query, Document.class, "assessment_profiles");
        return Optional.ofNullable(legacy).map(row -> {
            var completedAt = row.getDate("latest_valid_at");
            return new UserAssessmentProgressVo(row.getString("initial_level"), row.getString("current_level"),
                    row.getString("highest_level"), completedAt == null ? null : completedAt.toInstant(),
                    row.getString("latest_valid_attempt_id"), Boolean.TRUE.equals(row.getBoolean("development_data")));
        });
    }

    public boolean update(String userId, UserAssessmentProgressVo progress) {
        var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(userId)),
                new Update().set("assessment_progress", progress).set("updated_at", Instant.now()),
                UserProfile.class);
        return result.getMatchedCount() == 1;
    }
}
