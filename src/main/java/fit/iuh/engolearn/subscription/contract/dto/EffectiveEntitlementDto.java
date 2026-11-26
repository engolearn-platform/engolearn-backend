package fit.iuh.engolearn.subscription.contract.dto;

import java.time.Instant;
import java.util.List;

public record EffectiveEntitlementDto(String userId, long entitlementVersion, String policyScope,
        String policySnapshotId, long policyVersion, long policyActivationSequence,
        Instant effectiveAt, Instant validUntil, QuotaPolicySnapshotDto policySnapshot,
        String planCode, List<String> serviceRights, boolean managedProduct) {
    public EffectiveEntitlementDto {
        serviceRights = serviceRights == null ? List.of() : List.copyOf(serviceRights);
    }

    public EffectiveEntitlementDto(String userId, long entitlementVersion, String policyScope,
            String policySnapshotId, long policyVersion, long policyActivationSequence,
            Instant effectiveAt, Instant validUntil, QuotaPolicySnapshotDto policySnapshot) {
        this(userId, entitlementVersion, policyScope, policySnapshotId, policyVersion, policyActivationSequence,
                effectiveAt, validUntil, policySnapshot, policyScope, List.of(), false);
    }
}
