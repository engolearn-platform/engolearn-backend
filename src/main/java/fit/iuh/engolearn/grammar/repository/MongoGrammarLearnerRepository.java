package fit.iuh.engolearn.grammar.repository;

import fit.iuh.engolearn.grammar.model.GrammarLearnerLock;
import fit.iuh.engolearn.grammar.model.GrammarLessonProgress;
import fit.iuh.engolearn.grammar.model.GrammarLevelUnlock;
import fit.iuh.engolearn.grammar.model.GrammarPracticeSession;
import fit.iuh.engolearn.grammar.model.GrammarPublishedRevision;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarPracticeStatus;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Repository
@RequiredArgsConstructor
public class MongoGrammarLearnerRepository implements GrammarLearnerRepository {
    private final MongoTemplate mongo;
    private final GrammarTopicStorageRepository storage;
    private final GrammarAdminRepository transactions;

    @Override
    public <T> T transactForLearner(String userId, Supplier<T> action) {
        return transactions.transact(() -> {
            // The first write serializes all learner changes, including creation of an active session.
            try {
                mongo.upsert(Query.query(Criteria.where("id").is(userId)), new Update().inc("revision", 1),
                        GrammarLearnerLock.class);
            } catch (DuplicateKeyException concurrentInit) {
                throw new CannotAcquireLockException("Grammar learner lock is being initialized", concurrentInit);
            }
            return action.get();
        });
    }

    @Override
    public List<GrammarTopic> findPublishedTopics() {
        Query query = Query.query(Criteria.where("status").is(GrammarStatus.PUBLISHED))
                .with(Sort.by("level", "order", "code"));
        query.fields().exclude("lessons.draft").exclude("lessons.published");
        return storage.find(query);
    }

    @Override
    public Optional<GrammarTopic> findPublishedTopic(String topicId) {
        Query query = Query.query(Criteria.where("id").is(topicId).and("status").is(GrammarStatus.PUBLISHED));
        query.fields().exclude("lessons.draft").exclude("lessons.published.questions");
        return storage.find(query.limit(1)).stream().findFirst();
    }

    @Override
    public boolean fencePublishedLesson(String topicId, String lessonId) {
        var query = Query.query(Criteria.where("id").is(topicId).and("status").is(GrammarStatus.PUBLISHED));
        var topic = storage.find(query.limit(1)).stream().findFirst();
        if (topic.isEmpty() || topic.get().getLessons().stream().noneMatch(lesson -> lesson.getId().equals(lessonId)
                && lesson.getStatus() == GrammarStatus.PUBLISHED)) return false;
        query.addCriteria(Criteria.where("version").is(topic.get().getVersion()));
        // Admin child writes and learner finalization contend on this same parent.
        Update fence = new Update().inc("learnerPublicationFence", 1).inc("version", 0);
        return mongo.updateFirst(query, fence, GrammarTopic.class).getMatchedCount() == 1;
    }

    @Override
    public Optional<GrammarPublishedRevision> findRevision(String topicId, String lessonId, int contentVersion) {
        Query query = Query.query(Criteria.where("id").is(lessonId + ":" + contentVersion)
                .and("topicId").is(topicId).and("lessonId").is(lessonId).and("contentVersion").is(contentVersion));
        return Optional.ofNullable(mongo.findOne(query, GrammarPublishedRevision.class));
    }

    @Override
    public List<GrammarLessonProgress> findProgress(String userId) {
        return mongo.find(Query.query(Criteria.where("userId").is(userId)), GrammarLessonProgress.class);
    }

    @Override
    public List<GrammarLessonProgress> findTopicProgress(String userId, String topicId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("topicId").is(topicId));
        return mongo.find(query, GrammarLessonProgress.class);
    }

    @Override
    public Optional<GrammarLessonProgress> findLessonProgress(String userId, String lessonId) {
        return Optional.ofNullable(mongo.findById(progressId(userId, lessonId), GrammarLessonProgress.class));
    }

    @Override
    public void saveProgress(GrammarLessonProgress progress) {
        if (progress.getId() == null) {
            progress.setId(progressId(progress.getUserId(), progress.getLessonId()));
        }
        mongo.save(progress);
    }

    @Override
    public List<GrammarLevelUnlock> findUnlocks(String userId) {
        return mongo.find(Query.query(Criteria.where("userId").is(userId)), GrammarLevelUnlock.class);
    }

    @Override
    public void insertUnlock(GrammarLevelUnlock unlock) {
        unlock.setId(unlock.getUserId() + ":" + unlock.getLevel().name());
        mongo.insert(unlock);
    }

    @Override
    public Optional<GrammarPracticeSession> findOwnedSession(String userId, String sessionId) {
        Query query = Query.query(Criteria.where("id").is(sessionId).and("userId").is(userId));
        return Optional.ofNullable(mongo.findOne(query, GrammarPracticeSession.class));
    }

    @Override
    public Optional<GrammarPracticeSession> findActiveSession(String userId, String topicId, String lessonId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("topicId").is(topicId)
                .and("lessonId").is(lessonId).and("status").is(GrammarPracticeStatus.IN_PROGRESS));
        return Optional.ofNullable(mongo.findOne(query, GrammarPracticeSession.class));
    }

    @Override
    public void saveSession(GrammarPracticeSession session) {
        mongo.save(session);
    }

    private static String progressId(String userId, String lessonId) {
        return userId + ":" + lessonId;
    }
}
