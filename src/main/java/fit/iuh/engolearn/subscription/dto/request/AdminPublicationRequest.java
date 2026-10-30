package fit.iuh.engolearn.subscription.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record AdminPublicationRequest(
    @NotNull @Min(0) @Max(9007199254740991L) Long expectedRevision,
    @NotNull @Valid PublicationEffectRequest effect, @NotNull @Valid ConfigurationContentRequest content) {
    public record PublicationEffectRequest(@NotNull @Pattern(regexp="NEXT_LOCAL_MIDNIGHT|NOW|AT") String mode,
        @JsonProperty(required=true) Instant at) {}
    public record PlanBenefitRequest(@NotBlank @Size(max=128) String code, @NotBlank @Size(max=200) String title,
        @NotBlank @Size(max=2000) String description, @JsonProperty(required=true) @Size(max=200) String displayValue,
        @NotNull @Min(0) @Max(10000) Integer sortOrder) {}
    public record PlanBillingOptionRequest(@NotNull @Pattern(regexp="MONTHLY|ANNUAL") String billingPeriod,
        @NotNull @Min(0) @Max(9007199254740991L) Long amount,
        @NotNull @Pattern(regexp="[A-Z]{3}") String currency) {}
    public record QuotaRuleRequest(@NotBlank @Size(max=128) String quotaType, @NotBlank @Size(max=200) String displayName,
        @NotBlank @Size(max=128) String unit, @NotNull @Pattern(regexp="DAILY|WEEKLY|MONTHLY") String period,
        @NotNull @Min(0) @Max(9007199254740991L) Long limit,
        @NotNull @Min(0) @Max(10000) Integer displayOrder) {}
    public record ConfigurationContentRequest(@NotBlank @Size(max=200) String displayName,
        @JsonProperty(required=true) @Size(max=2000) String description,
        @NotNull @Min(0) @Max(10000) Integer displayOrder,
        @NotNull @Size(max=100) List<@NotNull @Valid PlanBenefitRequest> benefits,
        @NotNull @Size(max=2) List<@NotNull @Valid PlanBillingOptionRequest> billingOptions,
        @NotNull @Size(min=1,max=100) List<@NotNull @Valid QuotaRuleRequest> quotaRules) {}
}
