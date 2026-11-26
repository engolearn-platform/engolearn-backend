package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.model.UserProfile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserLevelPromotionRepository {
    private final MongoTemplate mongo;

    public Optional<CefrLevel> promoteIfLower(String userId, List<CefrLevel> lowerLevels, CefrLevel requested) {
        Criteria eligible = Criteria.where("_id").is(userId).orOperator(
                Criteria.where("cefr_level").in(lowerLevels), Criteria.where("cefr_level").is(null));
        // Placement integration: field-only CAS preserves higher levels and other profile/progress data.
        mongo.updateFirst(Query.query(eligible), new Update().set("cefr_level", requested)
                .set("updated_at", Instant.now()), UserProfile.class);
        return Optional.ofNullable(mongo.findById(userId, UserProfile.class)).map(UserProfile::getCefrLevel);
    }
}
