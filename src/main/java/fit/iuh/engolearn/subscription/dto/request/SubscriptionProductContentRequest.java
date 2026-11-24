package fit.iuh.engolearn.subscription.dto.request;

import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.PlanBenefitRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.PlanBillingOptionRequest;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest.QuotaRuleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SubscriptionProductContentRequest(
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 2000) String description,
        @NotNull @Min(0) @Max(10000) Integer displayOrder,
        @NotNull @Size(max = 100) List<@NotNull @Valid PlanBenefitRequest> benefits,
        @NotNull @Size(max = 2) List<@NotNull @Valid PlanBillingOptionRequest> billingOptions,
        @NotNull @Size(max = 100) List<@NotNull @Valid QuotaRuleRequest> quotaRules) {}
