package fit.iuh.engolearn.subscription.dto.response;
import java.util.List;
public record PlanCardResponse(String planCode, String displayName, String description, List<PlanBenefitResponse> benefits, List<PlanBillingOptionResponse> billingOptions) {
    public PlanCardResponse { benefits = List.copyOf(benefits); billingOptions = List.copyOf(billingOptions); }
}
