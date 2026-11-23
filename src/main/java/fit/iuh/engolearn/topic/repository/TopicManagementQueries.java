package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Repository
public class TopicManagementQueries {
    private final MongoTemplate mongo;

    public TopicManagementQueries(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Page<Topic> findPage(String search, CefrLevel cefr, TopicCategory category, Boolean published,
                                int page, int size) {
        Query filter = new Query();
        if (search != null && !search.isBlank()) {
            Pattern title = Pattern.compile(Pattern.quote(search.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filter.addCriteria(new Criteria().orOperator(Criteria.where("titleEn").regex(title),
                    Criteria.where("titleVi").regex(title)));
        }
        if (cefr != null) filter.addCriteria(Criteria.where("cefrLevel").is(cefr));
        if (category != null) filter.addCriteria(Criteria.where("category").is(category));
        if (published != null) filter.addCriteria(publication(published));

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        long total = mongo.count(filter, Topic.class);
        return new PageImpl<>(mongo.find(Query.of(filter).with(pageable), Topic.class), pageable, total);
    }

    public long countByPublication(boolean published) {
        return mongo.count(Query.query(publication(published)), Topic.class);
    }

    public Map<String, ItemStats> itemStats(Collection<String> topicIds) {
        if (topicIds.isEmpty()) return Map.of();

        List<Document> pipeline = List.of(
                new Document("$match", new Document("topic_id", new Document("$in", topicIds))
                        .append("topic_item_id", new Document("$ne", null))),
                new Document("$project", new Document("topic_id", 1).append("topic_item_id", 1)
                        .append("version", 1).append("status", 1).append("duration_minutes", 1)),
                new Document("$sort", new Document("version", -1).append("_id", -1)),
                new Document("$group", new Document("_id", new Document("topic", "$topic_id").append("item", "$topic_item_id"))
                        .append("status", new Document("$first", "$status"))
                        .append("duration", new Document("$first", "$duration_minutes"))),
                new Document("$match", new Document("status", new Document("$ne", "HIDDEN"))),
                new Document("$group", new Document("_id", "$_id.topic")
                        .append("count", new Document("$sum", 1))
                        .append("duration", new Document("$sum", "$duration"))));

        Map<String, ItemStats> stats = new HashMap<>();
        for (Document row : mongo.getCollection(mongo.getCollectionName(TopicItemVersion.class)).aggregate(pipeline)) {
            stats.put(row.getString("_id"), new ItemStats(((Number) row.get("count")).longValue(),
                    ((Number) row.get("duration")).longValue()));
        }
        return stats;
    }

    public record ItemStats(long count, long duration) {
        public static final ItemStats EMPTY = new ItemStats(0, 0);
    }

    private static Criteria publication(boolean published) {
        Criteria pointer = Criteria.where("latestPublishedVersion");
        return published ? pointer.nin(null, "") : pointer.in(null, "");
    }
}
