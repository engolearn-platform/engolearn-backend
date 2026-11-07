package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class MongoPlacementTestSetRepository implements PlacementTestSetRepository {
    private final MongoTemplate mongo;
    private final Environment environment;
    private final boolean allowDraft;

    public MongoPlacementTestSetRepository(MongoTemplate mongo, Environment environment,
                                           @Value("${engolearn.placement.test-set.allow-draft:false}") boolean allowDraft) {
        this.mongo = mongo;
        this.environment = environment;
        this.allowDraft = allowDraft;
    }

    @Override
    public Optional<PlacementTestSet> findPublished() {
        Optional<PlacementTestSet> published = select(PlacementTestSetStatus.PUBLISHED);
        if (published.isPresent() || !allowDraft || !environment.acceptsProfiles(Profiles.of("dev"))) {
            return published;
        }
        return select(PlacementTestSetStatus.DRAFT);
    }

    @Override
    public Optional<PlacementTestSet> findVersion(String id, int version) {
        PlacementTestSet set = mongo.findOne(Query.query(Criteria.where("_id").is(id).and("version").is(version)),
                PlacementTestSet.class);
        return set == null ? Optional.empty() : Optional.of(PlacementCopies.testSet(set));
    }

    private Optional<PlacementTestSet> select(PlacementTestSetStatus status) {
        List<PlacementTestSet> candidates = mongo.find(Query.query(
                        Criteria.where("status").is(status).and("items.19").exists(true)),
                PlacementTestSet.class);
        if (candidates.isEmpty()) return Optional.empty();
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return Optional.of(PlacementCopies.testSet(candidates.get(index)));
    }
}
