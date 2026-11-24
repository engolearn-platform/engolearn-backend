package fit.iuh.engolearn.subscription.dto.response;

import java.time.Instant;

public record SubscriptionProductVersionSummaryResponse(long version, Instant effectiveAt, String legacyScheduleStatus) {
    public SubscriptionProductVersionSummaryResponse(long version, Instant effectiveAt) { this(version, effectiveAt, null); }
}
