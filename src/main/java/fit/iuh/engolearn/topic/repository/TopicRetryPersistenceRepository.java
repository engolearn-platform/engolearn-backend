package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.topic.model.TopicRetryArchive;
import fit.iuh.engolearn.topic.model.TopicRetryLocator;
import fit.iuh.engolearn.topic.model.TopicRetryRuntime;
import fit.iuh.engolearn.topic.model.enums.RetryLocatorKind;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TopicRetryPersistenceRepository {
    private final MongoTemplate mongo;

    public boolean isAttemptLocator(String id) {
        return mongo.exists(Query.query(Criteria.where("_id").is(id)
                .and("kind").is(RetryLocatorKind.ATTEMPT)), TopicRetryLocator.class);
    }

    public TopicRetryLocator findLocator(String id) {
        return mongo.findById(id, TopicRetryLocator.class);
    }

    public TopicRetryLocator findLocatorByStartKey(String userId, String topicId, String key) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId)
                .and("topic_id").is(topicId).and("start_request_key").is(key)), TopicRetryLocator.class);
    }

    public void insertLocator(TopicRetryLocator locator) {
        mongo.insert(locator);
    }

    public TopicRetryRuntime findRuntime(String id) {
        return mongo.findById(id, TopicRetryRuntime.class);
    }

    public void insertRuntime(TopicRetryRuntime state) {
        mongo.insert(state);
    }

    public TopicRetryRuntime replaceRuntime(TopicRetryRuntime state, String userId, long expectedVersion) {
        Query query = Query.query(Criteria.where("_id").is(state.getId())
                .and("user_id").is(userId).and("version").is(expectedVersion));
        Update update = new Update().set("session_id", state.getSessionId()).set("scope", state.getScope())
                .set("status", state.getStatus()).set("started_at", state.getStartedAt())
                .set("completed_at", state.getCompletedAt()).set("items_snapshot", state.getItemsSnapshot())
                .set("completed_item_ids", state.getCompletedItemIds()).set("current_attempt", state.getCurrentAttempt())
                .set("official_results", state.getOfficialResults()).set("version", expectedVersion + 1);
        return mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), TopicRetryRuntime.class);
    }

    public void insertArchive(TopicRetryArchive archive) {
        mongo.insert(archive);
    }
}
