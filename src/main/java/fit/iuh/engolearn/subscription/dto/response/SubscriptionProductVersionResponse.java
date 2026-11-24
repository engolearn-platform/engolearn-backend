package fit.iuh.engolearn.subscription.dto.response;

import fit.iuh.engolearn.subscription.dto.request.SubscriptionProductContentRequest;
import java.time.Instant;

public record SubscriptionProductVersionResponse(long version, Instant effectiveAt,
        SubscriptionProductContentRequest content, String legacyScheduleStatus) {
    public SubscriptionProductVersionResponse(long version, Instant effectiveAt, SubscriptionProductContentRequest content) {
        this(version, effectiveAt, content, null);
    }
}
