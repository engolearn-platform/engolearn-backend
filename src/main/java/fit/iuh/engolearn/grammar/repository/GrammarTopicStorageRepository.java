package fit.iuh.engolearn.grammar.repository;

import fit.iuh.engolearn.grammar.mapper.GrammarLessonStorageMapper;
import fit.iuh.engolearn.grammar.model.GrammarLessonDocument;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class GrammarTopicStorageRepository {
    private final MongoTemplate mongo;

    List<GrammarTopic> find(Query query) {
        var pipeline = pipeline(query);
        if (!query.getSortObject().isEmpty()) pipeline.add(raw(new Document("$sort", query.getSortObject())));
        if (query.getSkip() > 0) pipeline.add(raw(new Document("$skip", query.getSkip())));
        if (query.getLimit() > 0) pipeline.add(raw(new Document("$limit", query.getLimit())));
        if (!query.getFieldsObject().isEmpty()) pipeline.add(raw(new Document("$project", query.getFieldsObject())));
        var topics = mongo.aggregate(Aggregation.newAggregation(pipeline), GrammarTopic.class, GrammarTopic.class).getMappedResults();
        for (var topic : topics) {
            topic.getLessons().sort(Comparator.comparing(lesson -> lesson.getOrder(), Comparator.nullsLast(Integer::compareTo)));
            topic.setLessonsLoaded(query.getFieldsObject().isEmpty());
        }
        return topics;
    }

    long count(Query query) {
        var pipeline = pipeline(query);
        pipeline.add(raw(new Document("$count", "total")));
        var result = mongo.aggregate(Aggregation.newAggregation(pipeline), GrammarTopic.class, Document.class).getUniqueMappedResult();
        return result == null ? 0 : ((Number) result.get("total")).longValue();
    }

    private List<AggregationOperation> pipeline(Query query) {
        var steps = new ArrayList<AggregationOperation>();
        steps.add(raw(new Document("$lookup", new Document("from", "grammar_lessons")
                .append("localField", "_id").append("foreignField", "topic_id").append("as", "__grammar_children"))));
        var separate = new Document("$map", new Document("input", "$__grammar_children").append("as", "lesson")
                .append("in", new Document("$mergeObjects", List.of("$$lesson", new Document("lesson_id", "$$lesson._id")))));
        var selected = new Document("$cond", List.of(new Document("$eq", List.of("$schema_version", 2)),
                separate, new Document("$ifNull", List.of("$lessons", List.of()))));
        steps.add(raw(new Document("$set", new Document("lessons", selected))));
        steps.add(raw(new Document("$unset", List.of("__grammar_children", "lessons._id", "lessons._class",
                "lessons.topic_id", "lessons.schema_version", "lessons.created_at", "lessons.updated_at"))));
        if (!query.getQueryObject().isEmpty()) {
            var entity = mongo.getConverter().getMappingContext().getPersistentEntity(GrammarTopic.class);
            var match = new QueryMapper(mongo.getConverter()).getMappedObject(query.getQueryObject(), entity);
            steps.add(raw(new Document("$match", match)));
        }
        return steps;
    }

    private static AggregationOperation raw(Document operation) {
        return context -> operation;
    }

    void insert(GrammarTopic topic) {
        requireTransaction();
        topic.setSchemaVersion(2);
        topic.setVersion(0L);
        topic.setLessonsLoaded(true);
        Instant now = Instant.now();
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        var document = topicDocument(topic);
        mongo.insert(document, "grammar_topics");
        saveChildren(topic, now);
    }

    void save(GrammarTopic topic) {
        if (!Objects.equals(topic.getSchemaVersion(), 2)) {
            mongo.save(topic);
            return;
        }
        requireTransaction();
        if (!topic.isLessonsLoaded()) throw new IllegalArgumentException("Cannot save a projected Grammar aggregate");
        long version = Objects.requireNonNull(topic.getVersion(), "Topic version");
        Instant now = Instant.now();
        var values = topicDocument(topic);
        values.remove("_id");
        values.remove("version");
        values.put("updated_at", now);
        values.put("order", topic.getOrder());
        values.put("order_before_archive", topic.getOrderBeforeArchive());
        var update = new Update().inc("version", 1).unset("lessons");
        values.forEach(update::set);
        // Every child mutation locks the parent first; Learner fences the same root.
        if (mongo.updateFirst(versionQuery(topic), update, "grammar_topics").getMatchedCount() != 1) {
            throw new OptimisticLockingFailureException("Grammar topic version changed");
        }
        saveChildren(topic, now);
        topic.setVersion(version + 1);
        topic.setUpdatedAt(now);
    }

    void delete(GrammarTopic topic) {
        if (Objects.equals(topic.getSchemaVersion(), 2)) requireTransaction();
        if (mongo.remove(versionQuery(topic), "grammar_topics").getDeletedCount() != 1) {
            throw new OptimisticLockingFailureException("Grammar topic version changed while deleting");
        }
        if (Objects.equals(topic.getSchemaVersion(), 2)) {
            mongo.remove(Query.query(Criteria.where("topicId").is(topic.getId())), GrammarLessonDocument.class);
        }
    }

    private void saveChildren(GrammarTopic topic, Instant now) {
        var ids = new HashSet<String>();
        for (var lesson : topic.getLessons()) {
            if (lesson.getId() == null || !ids.add(lesson.getId())) throw new DataIntegrityViolationException("Missing or duplicate lesson ID");
            var previous = mongo.findById(lesson.getId(), GrammarLessonDocument.class);
            if (previous != null && !previous.getTopicId().equals(topic.getId())) {
                throw new DataIntegrityViolationException("Lesson ID already belongs to another Grammar topic");
            }
            var child = GrammarLessonStorageMapper.document(topic.getId(), lesson);
            child.setCreatedAt(previous == null ? now : previous.getCreatedAt());
            child.setUpdatedAt(now);
            mongo.save(child);
        }
        var removed = Query.query(Criteria.where("topicId").is(topic.getId()));
        if (!ids.isEmpty()) removed.addCriteria(Criteria.where("id").nin(ids));
        mongo.remove(removed, GrammarLessonDocument.class);
    }

    private Document topicDocument(GrammarTopic topic) {
        var document = new Document();
        mongo.getConverter().write(topic, document);
        document.remove("lessons");
        return document;
    }

    private static Query versionQuery(GrammarTopic topic) {
        return Query.query(Criteria.where("_id").is(topic.getId()).and("version").is(topic.getVersion()));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Grammar V2 writes require a MongoDB transaction");
        }
    }
}
