package fit.iuh.engolearn.subscription.dto.response;

import fit.iuh.engolearn.subscription.dto.request.SubscriptionProductContentRequest;
import java.time.Instant;
import java.util.List;

public record SubscriptionProductPreviewResponse(SubscriptionProductContentRequest content, Instant effectiveAt,
        List<String> changedQuotaTypes) {
    public SubscriptionProductPreviewResponse { changedQuotaTypes = List.copyOf(changedQuotaTypes); }
}
