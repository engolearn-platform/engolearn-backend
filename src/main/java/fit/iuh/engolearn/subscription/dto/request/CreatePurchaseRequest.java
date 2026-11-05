package fit.iuh.engolearn.subscription.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
public record CreatePurchaseRequest(@NotNull @Pattern(regexp="PREMIUM") String planCode,
        @NotNull @Pattern(regexp="MONTHLY|ANNUAL") String billingPeriod) {}
