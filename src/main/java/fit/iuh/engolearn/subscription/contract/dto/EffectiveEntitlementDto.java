package fit.iuh.engolearn.subscription.contract.dto;

import java.time.Instant;

public record EffectiveEntitlementDto(String userId, long entitlementVersion, String policyScope,
        String policySnapshotId, long policyVersion, long policyActivationSequence,
        Instant effectiveAt, Instant validUntil, QuotaPolicySnapshotDto policySnapshot) {}
