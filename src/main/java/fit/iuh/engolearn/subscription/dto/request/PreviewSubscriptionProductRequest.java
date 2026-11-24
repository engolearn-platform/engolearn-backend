package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PreviewSubscriptionProductRequest(
        @NotNull @Min(0) @Max(9007199254740991L) Long expectedDraftRevision) {}
