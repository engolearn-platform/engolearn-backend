package fit.iuh.engolearn.quota.repository;

import fit.iuh.engolearn.quota.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface QuotaRepository {
    QuotaPolicySnapshot findSnapshot(String snapshotId);
    List<QuotaPolicySnapshot> findSnapshots();
    void insertSnapshot(QuotaPolicySnapshot snapshot);
    QuotaEntitlementProjection findEntitlement(String userId);
    void insertEntitlement(QuotaEntitlementProjection projection);
    boolean replaceEntitlement(long revision, QuotaEntitlementProjection projection);
    boolean guardEntitlement(String userId, long revision);
    QuotaUsageCounter findCounter(String userId, String quotaType);
    List<QuotaUsageCounter> findCountersForTypes(Set<String> quotaTypes);
    void insertCounter(QuotaUsageCounter counter);
    boolean replaceCounter(QuotaUsageCounter observed, QuotaUsageCounter next, Long maximumPriorUsed);
    boolean resetCounter(QuotaUsageCounter observed, Instant windowStart, String allowanceId,
            long retained, String nextAllowanceId, Instant effectiveAt, long activationSequence,
            String activationId, Instant updatedAt);
    QuotaUsageLedger findAcceptedUsage(String userId, String sourceFeature, String idempotencyKey);
    List<QuotaUsageLedger> findUsageRecordedBetween(String userId, String quotaType, Instant from, Instant until);
    void insertUsage(QuotaUsageLedger usage);
    QuotaActivationReceipt findActivationHead(String policyScope);
    boolean advanceActivationHead(String policyScope, long observedSequence, long nextSequence);
    void insertActivationRecord(QuotaActivationReceipt record);
    void ensureIndexes();
}
