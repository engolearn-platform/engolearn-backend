package fit.iuh.engolearn.speaking.repository;

import com.mongodb.client.AggregateIterable;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Repository
@RequiredArgsConstructor
public class SpeakingHistoryQueries {
    private static final String OWNER = "owner_id";
    private static final String DELETED = "deleted_at";
    private static final String PARENT = "snapshot.topic_id";
    private static final String CREATED = "created_at";
    private static final String SAVED_TITLE = "snapshot.topic_title";
    private static final String SCOPE_TITLE = "snapshot.title";
    private static final String SCOPE_TYPE = "snapshot.scope_type";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc(CREATED), Sort.Order.desc("_id"));
    private final MongoTemplate mongo;

    public Stream<ParentGroup> streamGroups(String ownerId, int batchSize) {
        var cursor = groups(ownerId).batchSize(batchSize).iterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(cursor, 0), false)
                .map(SpeakingHistoryQueries::readGroup).onClose(cursor::close);
    }

    AggregateIterable<Document> groups(String ownerId) {
        return mongo.getCollection(mongo.getCollectionName(SpeakingSession.class))
                .aggregate(groupPipeline(ownerId)).allowDiskUse(true);
    }

    private List<Document> groupPipeline(String ownerId) {
        Document parentTitle = new Document("$cond", List.of(hasText("$" + SAVED_TITLE), "$" + SAVED_TITLE,
                new Document("$cond", List.of(new Document("$eq", List.of("$" + SCOPE_TYPE, "TOPIC")),
                        "$" + SCOPE_TITLE, ""))));
        return List.of(
                new Document("$match", new Document(OWNER, ownerId).append(DELETED, null)
                        .append(PARENT, new Document("$type", "string").append("$ne", ""))),
                new Document("$set", new Document("savedTitle", parentTitle)),
                new Document("$set", new Document("hasSavedTitle", hasText("$savedTitle"))
                        .append("hasSavedLevel", hasText("$snapshot.level"))),
                new Document("$group", new Document("_id", "$" + PARENT)
                        .append("sessionCount", new Document("$sum", 1))
                        .append("latestCreatedAt", new Document("$max", "$" + CREATED))
                        .append("savedTitle", new Document("$top", latestAvailable("hasSavedTitle", "$savedTitle")))
                        .append("savedLevel", new Document("$top", latestAvailable("hasSavedLevel", "$snapshot.level")))),
                new Document("$sort", new Document("latestCreatedAt", -1).append("_id", 1)));
    }

    private Document hasText(String field) {
        return new Document("$regexMatch", new Document("input", new Document("$ifNull", List.of(field, "")))
                .append("regex", "\\S"));
    }

    private Document latestAvailable(String available, String output) {
        return new Document("sortBy", new Document(available, -1).append(CREATED, -1).append("_id", -1))
                .append("output", output);
    }

    private static ParentGroup readGroup(Document document) {
        String title = document.getString("savedTitle");
        String level = document.getString("savedLevel");
        var mostRecent = document.getDate("latestCreatedAt");
        return new ParentGroup(document.getString("_id"), ((Number) document.get("sessionCount")).longValue(),
                mostRecent == null ? null : mostRecent.toInstant(), StringUtils.hasText(title) ? title : null,
                StringUtils.hasText(level) ? CefrLevel.valueOf(level) : null);
    }

    public List<SpeakingSession> parentRows(String ownerId, String topicId, int page, int size) {
        return mongo.find(parentPageQuery(ownerId, topicId, page, size), SpeakingSession.class);
    }

    Query parentPageQuery(String ownerId, String topicId, int page, int size) {
        Query query = parentQuery(ownerId, topicId).with(NEWEST_FIRST).skip((long) page * size).limit(size + 1);
        query.fields().include(SCOPE_TYPE, PARENT, "snapshot.topic_item_id", SCOPE_TITLE, SAVED_TITLE,
                "snapshot.topic_item_title", CREATED, "committed_turn_count", "speech_duration_ms",
                "status", "assessment_status");
        return query;
    }

    public long parentCount(String ownerId, String topicId) {
        return mongo.count(parentQuery(ownerId, topicId), SpeakingSession.class);
    }

    public String savedParentTitle(String ownerId, String topicId) {
        Query query = parentQuery(ownerId, topicId).addCriteria(new Criteria().orOperator(
                Criteria.where(SAVED_TITLE).regex("\\S"),
                Criteria.where(SCOPE_TYPE).is("TOPIC").and(SCOPE_TITLE).regex("\\S")))
                .with(NEWEST_FIRST);
        query.fields().include(SAVED_TITLE, SCOPE_TITLE);
        SpeakingSession saved = mongo.findOne(query, SpeakingSession.class);
        if (saved == null) return null;
        String topicTitle = saved.getSnapshot().getTopicTitle();
        return StringUtils.hasText(topicTitle) ? topicTitle : saved.getSnapshot().getTitle();
    }

    private Query parentQuery(String ownerId, String topicId) {
        return Query.query(Criteria.where(OWNER).is(ownerId).and(DELETED).isNull().and(PARENT).is(topicId));
    }

    public record ParentGroup(String topicId, long sessionCount, Instant latestCreatedAt, String savedTitle,
                              CefrLevel savedLevel) {}
}
