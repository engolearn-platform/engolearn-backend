package fit.iuh.engolearn.subscription.repository;

import fit.iuh.engolearn.subscription.model.*;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoSubscriptionRepository implements SubscriptionRepository {
    private final MongoTemplate mongo;
    public SubscriptionAccount findAccount(String userId) { return mongo.findById(userId, SubscriptionAccount.class); }
    public List<SubscriptionPeriod> findPeriodsNewestFirst(String userId) {
        return mongo.find(Query.query(Criteria.where("user_id").is(userId)).with(Sort.by(Sort.Direction.DESC,"period_start_at")), SubscriptionPeriod.class);
    }
    public PolicyState findPolicyState(String scope) { return mongo.findById(scope, PolicyState.class); }
    public PolicyVersion findPolicyVersion(String id) { return mongo.findById(id, PolicyVersion.class); }
    public List<PolicyVersion> findPolicyVersions() { return mongo.findAll(PolicyVersion.class); }
    public List<PlanVersion> findPlanVersions() { return mongo.findAll(PlanVersion.class); }
    public long readConfigurationRevision() {
        AdminConfigurationState state=mongo.findById("GLOBAL",AdminConfigurationState.class);
        if(state==null) throw new IllegalStateException("Configuration state unavailable");
        return state.getRevision();
    }
    public boolean advanceConfigurationRevision(long revision) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is("GLOBAL").and("revision").is(revision)),
            new Update().inc("revision",1),AdminConfigurationState.class).getModifiedCount()==1;
    }
    public AdminPublicationReceipt findAdminPublicationReceipt(String actor,String key) {
        return mongo.findOne(Query.query(Criteria.where("actor_id").is(actor).and("operation").is("PUBLISH_CONFIGURATION").and("idempotency_key").is(key)),AdminPublicationReceipt.class);
    }
    public boolean replaceActivePolicy(PolicyState old,PolicyVersion next) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(old.getId()).and("revision").is(old.getRevision()).and("pending_version_id").is(null)),
            new Update().set("active_version_id",next.getId()).inc("revision",1),PolicyState.class).getModifiedCount()==1;
    }
    public List<PlanVersion> findPublishedPlans(Instant at) {
        return mongo.find(Query.query(Criteria.where("published").is(true).and("effective_at").lte(at))
            .with(Sort.by(Sort.Direction.DESC,"version")), PlanVersion.class);
    }
    public TrialReceipt findTrialReceipt(String userId, String key) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId).and("operation").is("ACTIVATE_TRIAL").and("idempotency_key").is(key)),TrialReceipt.class);
    }
    public boolean markTrialUsed(SubscriptionAccount account) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(account.getId()).and("revision").is(account.getRevision()).and("trial_used").is(false)),
            new Update().set("trial_used",true).inc("revision",1),SubscriptionAccount.class).getModifiedCount()==1;
    }
    public boolean advanceEntitlement(SubscriptionAccount account, String fingerprint) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(account.getId()).and("revision").is(account.getRevision())),
            new Update().set("entitlement_fingerprint", fingerprint).inc("entitlement_version", 1).inc("revision", 1),
            SubscriptionAccount.class).getModifiedCount() == 1;
    }
    public PolicyVersion publishPolicyVersion(PolicyVersion value) { return mongo.insert(value); }
    public PlanVersion publishPlanVersion(PlanVersion value) { return mongo.insert(value); }
    public PolicyState initializePolicyState(PolicyState value) { return mongo.insert(value); }
    public SubscriptionAccount initializeAccountHistory(SubscriptionAccount value) { return mongo.insert(value); }
    public SubscriptionPeriod createSubscriptionPeriod(SubscriptionPeriod value) { return mongo.insert(value); }
    public TrialReceipt recordTrialActivation(TrialReceipt value) { return mongo.insert(value); }
    public AdminPublicationReceipt recordAdminPublication(AdminPublicationReceipt value) { return mongo.insert(value); }
    public PublicationReceipt recordManifestPublication(PublicationReceipt value) { return mongo.insert(value); }
    public List<PolicyState> findDuePolicyStates(Instant at) { return mongo.find(Query.query(Criteria.where("pending_effective_at").lte(at).and("pending_version_id").ne(null)),PolicyState.class); }
    public boolean activatePendingPolicy(PolicyState state) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(state.getId()).and("revision").is(state.getRevision())),
            new Update().set("active_version_id",state.getPendingVersionId()).unset("pending_version_id").unset("pending_effective_at").inc("activation_sequence",1).inc("revision",1),PolicyState.class).getModifiedCount()==1;
    }
    public boolean schedulePolicy(PolicyState old, PolicyVersion next) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(old.getId()).and("revision").is(old.getRevision()).and("pending_version_id").is(null)),
            new Update().set("pending_version_id",next.getId()).set("pending_effective_at",next.getEffectiveAt()).inc("revision",1),PolicyState.class).getModifiedCount()==1;
    }
    public PublicationReceipt findManifestPublicationReceipt(String manifestId) {
        return mongo.findById(manifestId, PublicationReceipt.class);
    }

    public void ensurePublicationStorage() {
        for (Class<?> type : new Class<?>[]{AdminConfigurationState.class, AdminPublicationReceipt.class,
                PlanVersion.class, PolicyVersion.class, SubscriptionAccount.class, SubscriptionPeriod.class,
                TrialReceipt.class, PolicyState.class, PublicationReceipt.class})
            if (!mongo.collectionExists(type)) mongo.createCollection(type);
        var resolver = new org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        for (Class<?> type : new Class<?>[]{PlanVersion.class, PolicyVersion.class, SubscriptionPeriod.class, TrialReceipt.class, AdminPublicationReceipt.class})
            resolver.resolveIndexFor(type).forEach(index -> mongo.indexOps(type).createIndex(index));
        mongo.upsert(Query.query(Criteria.where("_id").is("GLOBAL")),
            new Update().setOnInsert("revision", 0L), AdminConfigurationState.class);
    }
}
