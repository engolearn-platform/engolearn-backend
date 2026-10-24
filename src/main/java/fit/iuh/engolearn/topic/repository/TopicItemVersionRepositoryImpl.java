package fit.iuh.engolearn.topic.repository;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TopicItemVersionRepositoryImpl implements TopicStatsCustom {

    private final MongoTemplate mongoTemplate;

    public TopicItemVersionRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public TopicItemStats statsByTopicId(String topicId) {
        return statsByTopicIds(List.of(topicId)).getOrDefault(topicId, TopicItemStats.EMPTY);
    }

    @Override
    public Map<String, TopicItemStats> statsByTopicIds(Collection<String> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }

        List<Document> pipeline = List.of(
                new Document("$match", new Document("topic_id", new Document("$in", topicIds))),
                new Document("$project", new Document("_id", 1)
                        .append("topic_id", 1)
                        .append("topic_item_id", 1)
                        .append("version", 1)
                        .append("status", 1)
                        .append("duration_minutes", 1)),
                new Document("$sort", new Document("topic_id", 1)
                        .append("topic_item_id", 1)
                        .append("version", -1)
                        .append("_id", -1)),
                new Document("$group", new Document("_id", "$topic_item_id")
                        .append("topicId", new Document("$first", "$topic_id"))
                        .append("status", new Document("$first", "$status"))
                        .append("durationMinutes", new Document("$first", "$duration_minutes"))),
                new Document("$match", new Document("status", new Document("$ne", "HIDDEN"))),
                new Document("$group", new Document("_id", "$topicId")
                        .append("itemCount", new Document("$sum", 1))
                        .append("totalDurationMinutes", new Document("$sum", "$durationMinutes"))));

        Map<String, TopicItemStats> stats = new HashMap<>();
        for (Document result : mongoTemplate.getCollection("topic_item_versions").aggregate(pipeline)) {
            String topicId = result.getString("_id");
            if (topicId != null) {
                stats.put(topicId, new TopicItemStats(
                        asLong(result.get("itemCount")),
                        asLong(result.get("totalDurationMinutes"))));
            }
        }
        return stats;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
