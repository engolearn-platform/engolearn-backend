package fit.iuh.engolearn.quota.repository;

import fit.iuh.engolearn.quota.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MongoQuotaRepository implements QuotaRepository {
    private final MongoTemplate mongo;

    public QuotaPolicySnapshot findSnapshot(String id) {
        return mongo.findOne(Query.query(Criteria.where("policy_snapshot_id").is(id)), QuotaPolicySnapshot.class);
    }
    public List<QuotaPolicySnapshot> findSnapshots() { return mongo.findAll(QuotaPolicySnapshot.class); }
    public void insertSnapshot(QuotaPolicySnapshot value) { mongo.insert(value); }
    public QuotaEntitlementProjection findEntitlement(String userId) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId)), QuotaEntitlementProjection.class);
    }
    public void insertEntitlement(QuotaEntitlementProjection value) { mongo.insert(value); }
    public boolean replaceEntitlement(long revision, QuotaEntitlementProjection next) {
        return mongo.updateFirst(Query.query(Criteria.where("user_id").is(next.getUserId()).and("revision").is(revision)),
                new Update().set("user_id", next.getUserId()).set("entitlement_version", next.getEntitlementVersion())
                        .set("policy_scope", next.getPolicyScope()).set("policy_snapshot_id", next.getPolicySnapshotId())
                        .set("policy_version", next.getPolicyVersion()).set("policy_activation_sequence", next.getPolicyActivationSequence())
                        .set("effective_at", next.getEffectiveAt()).set("valid_until", next.getValidUntil())
                        .set("revision", revision + 1).set("updated_at", next.getUpdatedAt()), QuotaEntitlementProjection.class)
                .getModifiedCount() == 1;
    }
    public boolean guardEntitlement(String userId, long revision) {
        return mongo.updateFirst(Query.query(Criteria.where("user_id").is(userId).and("revision").is(revision)),
                new Update().inc("revision", 1L), QuotaEntitlementProjection.class).getModifiedCount() == 1;
    }
    public QuotaUsageCounter findCounter(String userId, String type) {
        return mongo.findOne(Query.query(key(userId, type)), QuotaUsageCounter.class);
    }
    public List<QuotaUsageCounter> findCountersForTypes(Set<String> types) {
        return mongo.find(Query.query(Criteria.where("quota_type").in(types)), QuotaUsageCounter.class);
    }
    public void insertCounter(QuotaUsageCounter value) { mongo.insert(value); }
    public boolean replaceCounter(QuotaUsageCounter observed, QuotaUsageCounter next, Long maximumPriorUsed) {
        Criteria predicate = key(observed.getUserId(), observed.getQuotaType()).and("revision").is(observed.getRevision())
                .and("calendar_window_start_at").is(observed.getCalendarWindowStartAt()).and("allowance_id").is(observed.getAllowanceId());
        if (maximumPriorUsed != null) predicate.and("used_amount").lte(maximumPriorUsed);
        Update update = new Update().set("user_id", next.getUserId()).set("quota_type", next.getQuotaType())
                .set("used_amount", next.getUsedAmount()).set("unit", next.getUnit()).set("period", next.getPeriod())
                .set("calendar_window_start_at", next.getCalendarWindowStartAt()).set("calendar_window_end_at", next.getCalendarWindowEndAt())
                .set("allowance_start_at", next.getAllowanceStartAt()).set("allowance_id", next.getAllowanceId())
                .set("updated_at", next.getUpdatedAt()).set("revision", next.getRevision());
        return mongo.updateFirst(Query.query(predicate), update, QuotaUsageCounter.class).getModifiedCount() == 1;
    }
    public boolean resetCounter(QuotaUsageCounter observed, Instant windowStart, String allowanceId,
            long retained, String nextAllowanceId, Instant effectiveAt, long sequence, String activationId, Instant updatedAt) {
        return mongo.updateFirst(Query.query(key(observed.getUserId(), observed.getQuotaType()).and("revision").is(observed.getRevision())
                .and("allowance_id").is(allowanceId).and("calendar_window_start_at").is(windowStart)),
                new Update().set("used_amount", retained).set("allowance_id", nextAllowanceId).set("allowance_start_at", effectiveAt)
                        .set("last_applied_activation_sequence", sequence).set("last_applied_policy_activation_id", activationId)
                        .set("revision", observed.getRevision() + 1).set("updated_at", updatedAt), QuotaUsageCounter.class)
                .getModifiedCount() == 1;
    }
    public QuotaUsageLedger findAcceptedUsage(String userId, String feature, String key) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId).and("source_feature").is(feature)
                .and("idempotency_key").is(key)), QuotaUsageLedger.class);
    }
    public List<QuotaUsageLedger> findUsageRecordedBetween(String userId, String type, Instant start, Instant end) {
        return mongo.find(Query.query(key(userId, type).and("recorded_at").gte(start).lt(end)), QuotaUsageLedger.class);
    }
    public void insertUsage(QuotaUsageLedger value) { mongo.insert(value); }
    public QuotaActivationReceipt findActivationHead(String scope) {
        return mongo.findOne(Query.query(Criteria.where("kind").is("SCOPE_ORDER").and("policy_scope").is(scope)), QuotaActivationReceipt.class);
    }
    public boolean advanceActivationHead(String scope, long observed, long next) {
        return mongo.updateFirst(Query.query(Criteria.where("kind").is("SCOPE_ORDER").and("policy_scope").is(scope)
                .and("activation_sequence").is(observed)), new Update().set("activation_sequence", next), QuotaActivationReceipt.class)
                .getModifiedCount() == 1;
    }
    public void insertActivationRecord(QuotaActivationReceipt value) { mongo.insert(value); }
    public void ensureIndexes() {
        var resolver = new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        for (Class<?> type : new Class<?>[]{QuotaPolicySnapshot.class, QuotaEntitlementProjection.class,
                QuotaUsageCounter.class, QuotaUsageLedger.class, QuotaActivationReceipt.class})
            resolver.resolveIndexFor(type).forEach(index -> mongo.indexOps(type).createIndex(index));
    }
    private Criteria key(String userId, String type) { return Criteria.where("user_id").is(userId).and("quota_type").is(type); }
}
