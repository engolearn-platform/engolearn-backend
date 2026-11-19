package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class MongoPlacementAttemptTestSetRepository implements PlacementAttemptTestSetRepository {
    private final MongoTemplate mongo;

    @Override
    public List<PlacementAttemptTestSetVersion> findVersionsByCode(String code, Integer version) {
        Criteria criteria = Criteria.where("code").regex(Pattern.compile("\\A" + Pattern.quote(code) + "\\z",
                Pattern.CASE_INSENSITIVE));
        if (version != null) criteria.and("version").is(version);
        return findVersions(criteria);
    }

    @Override
    public List<PlacementAttemptTestSetVersion> findVersionsById(Set<String> ids) {
        if (ids.isEmpty()) return List.of();
        return findVersions(Criteria.where("_id").in(ids));
    }

    private List<PlacementAttemptTestSetVersion> findVersions(Criteria criteria) {
        Document projection = new Document("code", 1).append("version", 1)
                .append("question_count", new Document("$cond", List.of(new Document("$isArray", "$items"),
                        new Document("$size", "$items"), 0)));
        var aggregation = Aggregation.newAggregation(PlacementTestSet.class, Aggregation.match(criteria),
                Aggregation.stage(new Document("$project", projection)));
        return mongo.aggregate(aggregation, Document.class).getMappedResults().stream()
                .map(set -> new PlacementAttemptTestSetVersion(set.get("_id").toString(),
                        set.getInteger("version"), set.getString("code"), set.getInteger("question_count")))
                .toList();
    }
}
