package fit.iuh.engolearn.subscription.repository;

import fit.iuh.engolearn.subscription.model.*;
import java.time.Instant;
import java.util.List;

public interface SubscriptionRepository {
    SubscriptionAccount findAccount(String userId);
    List<SubscriptionPeriod> findPeriodsNewestFirst(String userId);
    PolicyState findPolicyState(String scope);
    PolicyVersion findPolicyVersion(String id);
    List<PolicyVersion> findPolicyVersions();
    List<PlanVersion> findPlanVersions();
    long readConfigurationRevision();
    boolean advanceConfigurationRevision(long revision);
    AdminPublicationReceipt findAdminPublicationReceipt(String actor,String key);
    boolean replaceActivePolicy(PolicyState old,PolicyVersion next);
    List<PlanVersion> findPublishedPlans(Instant at);
    TrialReceipt findTrialReceipt(String userId, String key);
    boolean markTrialUsed(SubscriptionAccount account);
    void supersedeTrial(String periodId, java.time.Instant paidAt);
    boolean advanceEntitlement(SubscriptionAccount account, String fingerprint);
    PolicyVersion publishPolicyVersion(PolicyVersion value);
    PlanVersion publishPlanVersion(PlanVersion value);
    PolicyState initializePolicyState(PolicyState value);
    SubscriptionAccount initializeAccountHistory(SubscriptionAccount value);
    SubscriptionPeriod createSubscriptionPeriod(SubscriptionPeriod value);
    TrialReceipt recordTrialActivation(TrialReceipt value);
    AdminPublicationReceipt recordAdminPublication(AdminPublicationReceipt value);
    PublicationReceipt recordManifestPublication(PublicationReceipt value);
    List<PolicyState> findDuePolicyStates(Instant at);
    boolean activatePendingPolicy(PolicyState state);
    boolean schedulePolicy(PolicyState old, PolicyVersion next);
    PublicationReceipt findManifestPublicationReceipt(String manifestId);
    void ensurePublicationStorage();
}
