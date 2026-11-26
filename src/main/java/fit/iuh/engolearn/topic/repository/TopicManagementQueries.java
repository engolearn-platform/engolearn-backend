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

import java.util.ArrayList;
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

    public Page<Topic> findPage(String search, CefrLevel cefr, TopicCategory category, String status,
                                int page, int size) {
        Query filter = new Query();
        List<Criteria> criteria = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            Pattern title = Pattern.compile(Pattern.quote(search.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            criteria.add(new Criteria().orOperator(Criteria.where("titleEn").regex(title),
                    Criteria.where("titleVi").regex(title)));
        }
        if (cefr != null) criteria.add(Criteria.where("cefrLevel").is(cefr));
        if (category != null) criteria.add(Criteria.where("category").is(category));
        if (status != null) criteria.add(publication(status));
        if (criteria.size() == 1) filter.addCriteria(criteria.get(0));
        else if (!criteria.isEmpty()) filter.addCriteria(new Criteria().andOperator(criteria.toArray(Criteria[]::new)));

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        long total = mongo.count(filter, Topic.class);
        return new PageImpl<>(mongo.find(Query.of(filter).with(pageable), Topic.class), pageable, total);
    }

    public long countByPublication(String status) {
        return mongo.count(Query.query(publication(status)), Topic.class);
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

    private static Criteria publication(String status) {
        Criteria pointer = Criteria.where("latestPublishedVersion");
        return switch (status) {
            case "published" -> new Criteria().andOperator(pointer.nin(null, ""), Criteria.where("publicationSuspended").ne(true));
            case "unpublished" -> new Criteria().andOperator(pointer.nin(null, ""), Criteria.where("publicationSuspended").is(true));
            case "draft" -> pointer.in(null, "");
            default -> throw new IllegalArgumentException("Unsupported Topic publication status: " + status);
        };
    }
}
