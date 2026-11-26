package fit.iuh.engolearn.subscription.dto.request;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
public record CreatePurchaseRequest(@NotNull @Pattern(regexp="[A-Z][A-Z0-9_-]{0,63}") String planCode,
        @NotNull @Pattern(regexp="MONTHLY|ANNUAL") String billingPeriod,
        @NotNull @Min(1) @Max(9007199254740991L) Long expectedVersion) {
    public CreatePurchaseRequest(String planCode, String billingPeriod) { this(planCode, billingPeriod, null); }
}
