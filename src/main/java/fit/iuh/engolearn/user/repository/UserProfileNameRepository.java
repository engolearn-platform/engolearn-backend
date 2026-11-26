package fit.iuh.engolearn.user.repository;

import fit.iuh.engolearn.user.model.UserProfile;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserProfileNameRepository {
    private final MongoTemplate mongo;

    public Optional<UserProfile> updateDisplayName(String userId, String displayName) {
        var update = new Update().set("updated_at", Instant.now());
        if (displayName == null) update.unset("display_name");
        else update.set("display_name", displayName);
        return Optional.ofNullable(mongo.findAndModify(
                Query.query(Criteria.where("_id").is(userId)), update,
                FindAndModifyOptions.options().returnNew(true), UserProfile.class));
    }
}
