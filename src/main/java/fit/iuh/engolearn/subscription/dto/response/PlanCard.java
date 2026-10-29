package fit.iuh.engolearn.subscription.dto.response;
import java.util.List;
public record PlanCard(String planCode, String displayName, String description, List<PlanBenefit> benefits, List<PlanBillingOption> billingOptions) {
    public PlanCard { benefits = List.copyOf(benefits); billingOptions = List.copyOf(billingOptions); }
}
