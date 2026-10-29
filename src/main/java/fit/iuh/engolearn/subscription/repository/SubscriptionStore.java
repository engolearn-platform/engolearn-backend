package fit.iuh.engolearn.subscription.repository;

import fit.iuh.engolearn.subscription.model.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionStore {
    private final MongoTemplate mongo;
    public SubscriptionStore(MongoTemplate mongo) { this.mongo=mongo; }
    public SubscriptionAccount account(String userId) { return mongo.findById(userId, SubscriptionAccount.class); }
    public List<SubscriptionPeriod> periods(String userId) {
        return mongo.find(Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC,"periodStartAt")), SubscriptionPeriod.class);
    }
    public PolicyState state(String scope) { return mongo.findById(scope, PolicyState.class); }
    public PolicyVersion policy(String id) { return mongo.findById(id, PolicyVersion.class); }
    public List<PolicyVersion> policies() { return mongo.findAll(PolicyVersion.class); }
    public List<PlanVersion> plans(Instant at) {
        return mongo.find(Query.query(Criteria.where("published").is(true).and("effectiveAt").lte(at))
            .with(Sort.by(Sort.Direction.DESC,"version")), PlanVersion.class);
    }
    public TrialReceipt receipt(String userId, String key) {
        return mongo.findOne(Query.query(Criteria.where("userId").is(userId).and("operation").is("ACTIVATE_TRIAL").and("idempotencyKey").is(key)),TrialReceipt.class);
    }
    public boolean markTrial(SubscriptionAccount account) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(account.userId()).and("revision").is(account.revision()).and("trialUsed").is(false)),
            new Update().set("trialUsed",true).inc("revision",1).inc("entitlementVersion",2),SubscriptionAccount.class).getModifiedCount()==1;
    }
    public <T> T insert(T value) { return mongo.insert(value); }
    public List<PolicyState> due(Instant at) { return mongo.find(Query.query(Criteria.where("pendingEffectiveAt").lte(at).and("pendingVersionId").ne(null)),PolicyState.class); }
    public boolean activate(PolicyState state) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(state.scope()).and("revision").is(state.revision())),
            new Update().set("activeVersionId",state.pendingVersionId()).unset("pendingVersionId").unset("pendingEffectiveAt").inc("activationSequence",1).inc("revision",1),PolicyState.class).getModifiedCount()==1;
    }
    public boolean schedule(PolicyState old, PolicyVersion next) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(old.scope()).and("revision").is(old.revision()).and("pendingVersionId").is(null)),
            new Update().set("pendingVersionId",next.id()).set("pendingEffectiveAt",next.effectiveAt()).inc("revision",1),PolicyState.class).getModifiedCount()==1;
    }
}
