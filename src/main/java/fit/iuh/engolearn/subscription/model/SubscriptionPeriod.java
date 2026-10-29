package fit.iuh.engolearn.subscription.model;
import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshot;
import fit.iuh.engolearn.subscription.dto.response.CurrentBenefits;
import fit.iuh.engolearn.subscription.dto.response.PlanBillingOption;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_periods")
public record SubscriptionPeriod(@Id String id, String userId, String kind, String state,
    Instant periodStartAt, Instant periodEndAt, QuotaPolicySnapshot snapshot, CurrentBenefits benefits,
    List<PlanBillingOption> prices) {
    public SubscriptionPeriod { prices=List.copyOf(prices); }
}
