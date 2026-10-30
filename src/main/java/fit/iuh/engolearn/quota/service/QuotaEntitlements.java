package fit.iuh.engolearn.quota.service;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.contract.event.EntitlementChangedEvent;
import java.time.Instant;
public interface QuotaEntitlements {
    EffectiveEntitlementDto resolve(String userId, Instant at);
    EffectiveEntitlementDto authoritative(String userId, Instant at);
    void guardAcceptedUsage(String userId);
    void onEntitlementChanged(EntitlementChangedEvent event);
    EffectiveEntitlementDto project(EffectiveEntitlementDto next);
    void saveSnapshot(QuotaPolicySnapshotDto snapshot);
    QuotaRuleDto rule(EffectiveEntitlementDto entitlement, String type);
}
