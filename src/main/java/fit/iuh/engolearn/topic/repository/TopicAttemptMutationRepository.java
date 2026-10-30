package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.vo.CompletionResultVo;
import fit.iuh.engolearn.topic.model.vo.ContentMutationReceiptVo;
import fit.iuh.engolearn.topic.model.vo.FurthestReachedVo;
import fit.iuh.engolearn.topic.model.vo.QuizProgressVo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TopicAttemptMutationRepository {
    private final MongoTemplate mongo;

    public UserTopicItemAttempt findByStartKey(String userId, String topicId, String key) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId)
                .and("topic_id").is(topicId).and("start_request_key").is(key)), UserTopicItemAttempt.class);
    }

    public UserTopicItemAttempt advance(UserTopicItemAttempt expected, FurthestReachedVo position,
            long revision, Instant accessedAt, ContentMutationReceiptVo receipt) {
        Update update = new Update().set("furthest_reached", position).set("version", revision)
                .set("last_accessed_at", accessedAt).push("mutation_receipts", receipt);
        return mongo.findAndModify(expectedVersion(expected), update,
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
    }

    public UserTopicItemAttempt recordAnswer(UserTopicItemAttempt expected, List<QuizProgressVo> progress,
            long revision, Instant accessedAt, ContentMutationReceiptVo receipt,
            FurthestReachedVo advanced, CompletionResultVo completedResult) {
        Update update = new Update().set("quiz_progress", progress).set("version", revision)
                .set("last_accessed_at", accessedAt).push("mutation_receipts", receipt);
        if (advanced != null) {
            update.set("furthest_reached", advanced);
        }
        if (completedResult != null) {
            update.set("status", AttemptStatus.COMPLETED).set("furthest_reached", null)
                    .set("completed_at", accessedAt).set("completion_result", completedResult);
        }
        return mongo.findAndModify(expectedVersion(expected), update,
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
    }

    public UserTopicItemAttempt complete(UserTopicItemAttempt expected, CompletionResultVo result,
            Instant completedAt, ContentMutationReceiptVo receipt) {
        Update update = new Update().set("status", AttemptStatus.COMPLETED).set("furthest_reached", null)
                .set("completed_at", completedAt).set("completion_result", result)
                .set("version", receipt.getRevision()).set("last_accessed_at", completedAt)
                .push("mutation_receipts", receipt);
        return mongo.findAndModify(expectedVersion(expected), update,
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
    }

    public UserTopicItemProgress findCompletedProgress(UserTopicItemAttempt attempt) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(attempt.getUserId())
                .and("topic_item_id").is(attempt.getTopicItemId())
                .and("last_completed_attempt_id").is(attempt.getId())), UserTopicItemProgress.class);
    }

    public void abandonHidden(UserTopicItemAttempt expected, long revision) {
        mongo.findAndModify(expectedVersion(expected), new Update().set("status", AttemptStatus.ABANDONED)
                .set("abandoned_reason", "ITEM_HIDDEN").set("version", revision), UserTopicItemAttempt.class);
    }

    public UserTopicItemAttempt recordAccess(UserTopicItemAttempt expected, Instant accessedAt,
            ContentMutationReceiptVo receipt) {
        Update update = new Update().set("version", expected.getVersion()).set("last_accessed_at", accessedAt)
                .push("mutation_receipts", receipt);
        return mongo.findAndModify(expectedVersion(expected), update,
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
    }

    private Query expectedVersion(UserTopicItemAttempt attempt) {
        return Query.query(Criteria.where("_id").is(attempt.getId())
                .and("user_id").is(attempt.getUserId()).and("version").is(attempt.getVersion()));
    }
}
