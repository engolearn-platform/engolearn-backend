package fit.iuh.engolearn.subscription.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveSubscriptionProductDraftRequest(
        @NotNull @Min(0) @Max(9007199254740991L) Long expectedDraftRevision,
        @NotNull @Valid SubscriptionProductContentRequest content) {}
