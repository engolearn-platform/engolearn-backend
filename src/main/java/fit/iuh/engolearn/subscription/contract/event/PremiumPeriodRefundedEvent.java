package fit.iuh.engolearn.subscription.contract.event;

import java.time.Instant;

/** Verified full refund of a single Premium period, after purchase/provider correlation. */
public record PremiumPeriodRefundedEvent(String userId, String policySnapshotId, Instant effectiveAt) {}
