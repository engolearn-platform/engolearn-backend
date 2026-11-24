package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PublishSubscriptionProductRequest(
        @NotNull @Min(0) @Max(9007199254740991L) Long expectedDraftRevision,
        Boolean replaceScheduled, Long expectedScheduleVersion, Instant expectedScheduleEffectiveAt) {
    public PublishSubscriptionProductRequest(Long expectedDraftRevision) { this(expectedDraftRevision, null, null, null); }
    public PublishSubscriptionProductRequest(Long expectedDraftRevision, Boolean replaceScheduled) {
        this(expectedDraftRevision, replaceScheduled, null, null);
    }
}
