package fit.iuh.engolearn.assessment.repository;

import fit.iuh.engolearn.assessment.model.AssessmentAudioAsset;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@RequiredArgsConstructor
public class AssessmentAudioRepository {
    private final MongoTemplate mongo;

    public AssessmentAudioAsset find(String id) {
        return mongo.findById(id, AssessmentAudioAsset.class);
    }

    public AssessmentAudioAsset findSourceByMediaId(String mediaId) {
        return mongo.findOne(Query.query(where("media_id").is(mediaId).and("_id").regex("^[a-f0-9]{64}$")),
                AssessmentAudioAsset.class);
    }

    public boolean reserve(String id, String token, Instant now, Instant until) {
        var expired = Query.query(where("_id").is(id).and("media_id").is(null).and("lease_until").lte(now));
        var lease = new Update().set("lease_token", token).set("lease_until", until);
        if (mongo.updateFirst(expired, lease, AssessmentAudioAsset.class).getMatchedCount() == 1) return true;
        try {
            mongo.insert(new AssessmentAudioAsset(id, null, 0, token, until));
            return true;
        } catch (DuplicateKeyException concurrentReservation) {
            return false;
        }
    }

    public boolean complete(AssessmentAudioAsset asset, String token) {
        return mongo.updateFirst(pending(asset.getId(), token),
                new Update().set("media_id", asset.getMediaId()).set("duration_seconds", asset.getDurationSeconds())
                        .unset("lease_token").unset("lease_until"), AssessmentAudioAsset.class).getMatchedCount() == 1;
    }

    public void release(String id, String token) {
        mongo.remove(pending(id, token), AssessmentAudioAsset.class);
    }

    public void replaceSoundCheck(AssessmentAudioAsset asset) {
        mongo.save(asset);
    }

    private Query pending(String id, String token) {
        return Query.query(where("_id").is(id).and("lease_token").is(token).and("media_id").is(null));
    }
}
