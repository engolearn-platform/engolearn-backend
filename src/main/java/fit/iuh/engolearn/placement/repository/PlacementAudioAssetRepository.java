package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAudioAsset;
import fit.iuh.engolearn.placement.model.enums.PlacementAudioAssetStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class PlacementAudioAssetRepository {
    private final MongoTemplate mongo;
    public Optional<PlacementAudioAsset> findById(String hash) {
        return Optional.ofNullable(mongo.findById(hash, PlacementAudioAsset.class));
    }
    public List<PlacementAudioAsset> findAll() {
        return mongo.find(Query.query(Criteria.where("status").is(PlacementAudioAssetStatus.READY)), PlacementAudioAsset.class);
    }
    public boolean reserve(String hash, String title, double duration) {
        var failed = Query.query(Criteria.where("_id").is(hash).and("status").is(PlacementAudioAssetStatus.FAILED));
        if (mongo.updateFirst(failed, new Update().set("status", PlacementAudioAssetStatus.UPLOADING)
                .set("updated_at", Instant.now()), PlacementAudioAsset.class).getModifiedCount() == 1) return true;
        try {
            mongo.insert(PlacementAudioAsset.builder().id(hash).title(title).durationSeconds(duration)
                    .status(PlacementAudioAssetStatus.UPLOADING).build());
            return true;
        } catch (DuplicateKeyException busy) { return false; }
    }
    public void complete(String hash, String mediaId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(hash).and("status").is(PlacementAudioAssetStatus.UPLOADING)),
                new Update().set("media_id", mediaId).set("status", PlacementAudioAssetStatus.READY)
                        .set("updated_at", Instant.now()), PlacementAudioAsset.class);
    }
    public void fail(String hash) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(hash).and("status").is(PlacementAudioAssetStatus.UPLOADING)),
                new Update().set("status", PlacementAudioAssetStatus.FAILED).set("updated_at", Instant.now()), PlacementAudioAsset.class);
    }
}
