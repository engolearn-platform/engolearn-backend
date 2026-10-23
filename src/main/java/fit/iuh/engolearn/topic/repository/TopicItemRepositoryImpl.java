package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoTemplate aggregation implementation of {@link TopicStatsCustom}
 * (design record F05 §3.6/§3.5). Registered as {@code TopicItemRepository}'s
 * custom implementation via the {@code <RepositoryInterface>Impl} naming rule —
 * do not rename to {@code TopicStatsCustomImpl}.
 *
 * <p>One aggregation per request: match {@code topicId IN (...)} and
 * {@code status != ARCHIVED}, then group by topic summing
 * {@code context.durationSec} (MongoDB {@code $sum} ignores null/missing
 * values, i.e. coalesces them to 0). Property names are mapped to their
 * {@code @Field} names by the aggregation context.</p>
 */
public class TopicItemRepositoryImpl implements TopicStatsCustom {

    private static final String ITEM_COUNT = "itemCount";
    private static final String TOTAL_DURATION_SEC = "totalDurationSec";

    private final MongoTemplate mongoTemplate;

    public TopicItemRepositoryImpl(MongoTemplate mongoTemplate) {
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
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("topicId").in(topicIds)
                        .and("status").ne(ContentStatus.ARCHIVED)),
                Aggregation.group("topicId")
                        .count().as(ITEM_COUNT)
                        .sum("context.durationSec").as(TOTAL_DURATION_SEC));

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, TopicItem.class, Document.class);

        Map<String, TopicItemStats> stats = new HashMap<>();
        for (Document document : results.getMappedResults()) {
            String topicId = document.getString("_id");
            if (topicId == null) {
                continue;
            }
            stats.put(topicId, new TopicItemStats(asLong(document.get(ITEM_COUNT)), asLong(document.get(TOTAL_DURATION_SEC))));
        }
        return stats;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
