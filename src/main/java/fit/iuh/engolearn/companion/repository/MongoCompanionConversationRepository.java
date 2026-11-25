package fit.iuh.engolearn.companion.repository;

import fit.iuh.engolearn.companion.model.CompanionConversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoCompanionConversationRepository implements CompanionConversationRepository {
    private final MongoTemplate mongo;

    @Override public CompanionConversation insert(CompanionConversation conversation) { return mongo.insert(conversation); }

    @Override public Optional<CompanionConversation> findOwned(String id, String learnerId) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("_id").is(id).and("learner_id").is(learnerId)), CompanionConversation.class));
    }

    @Override public List<CompanionConversation> listOwned(String learnerId, Instant before, String beforeId, int limit) {
        Criteria criteria = Criteria.where("learner_id").is(learnerId);
        if (before != null) criteria.andOperator(new Criteria().orOperator(
                Criteria.where("updated_at").lt(before), Criteria.where("updated_at").is(before).and("_id").lt(beforeId)));
        return mongo.find(Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "updated_at", "_id")).limit(limit), CompanionConversation.class);
    }

    @Override public Optional<CompanionConversation> admitNew(String id, String learnerId, String turnId,
            long expectedSequence, Instant now, Instant leaseUntil, int maxMessages) {
        Criteria criteria = available(id, learnerId, now).and("message_count").lte(maxMessages - 2)
                .and("next_sequence").is(expectedSequence);
        return Optional.ofNullable(mongo.findAndModify(Query.query(criteria),
                new Update().inc("next_sequence", 1).inc("active_turn_generation", 1).inc("message_count", 1)
                        .set("updated_at", now).set("pending_user_sequence", expectedSequence)
                        .set("active_turn_id", turnId).set("active_turn_lease_until", leaseUntil).set("pending_turn_id", turnId),
                FindAndModifyOptions.options().returnNew(true), CompanionConversation.class));
    }

    @Override public Optional<CompanionConversation> claimRetry(String id, String learnerId, String turnId, Instant now,
            Instant leaseUntil, int maxMessages) {
        Criteria criteria = available(id, learnerId, now).and("pending_turn_id").is(turnId)
                .and("message_count").lt(maxMessages);
        return Optional.ofNullable(mongo.findAndModify(Query.query(criteria), new Update().inc("active_turn_generation", 1)
                        .set("updated_at", now).set("active_turn_id", turnId).set("active_turn_lease_until", leaseUntil),
                FindAndModifyOptions.options().returnNew(true), CompanionConversation.class));
    }

    @Override public boolean finish(String id, String learnerId, String turnId, long generation, long expectedSequence, Instant now) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("learner_id").is(learnerId)
                        .and("active_turn_id").is(turnId).and("active_turn_generation").is(generation)
                        .and("active_turn_lease_until").gt(now).and("next_sequence").is(expectedSequence)),
                new Update().inc("message_count", 1).inc("next_sequence", 1).set("updated_at", now).unset("active_turn_id").unset("active_turn_lease_until")
                        .unset("pending_turn_id").unset("pending_user_sequence"), CompanionConversation.class).getModifiedCount() == 1;
    }

    @Override public boolean release(String id, String learnerId, String turnId, long generation, Instant now) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("learner_id").is(learnerId)
                        .and("active_turn_id").is(turnId).and("active_turn_generation").is(generation)),
                new Update().set("updated_at", now).unset("active_turn_id").unset("active_turn_lease_until"), CompanionConversation.class).getModifiedCount() == 1;
    }

    @Override public boolean deleteOwned(String id, String learnerId) {
        return mongo.remove(Query.query(Criteria.where("_id").is(id).and("learner_id").is(learnerId)), CompanionConversation.class).getDeletedCount() == 1;
    }

    private Criteria available(String id, String learnerId, Instant now) {
        return Criteria.where("_id").is(id).and("learner_id").is(learnerId)
                .andOperator(new Criteria().orOperator(Criteria.where("active_turn_id").exists(false),
                        Criteria.where("active_turn_lease_until").lte(now)));
    }
}
