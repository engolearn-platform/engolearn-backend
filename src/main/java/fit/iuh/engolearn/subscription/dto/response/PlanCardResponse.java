package fit.iuh.engolearn.subscription.dto.response;
import java.util.List;
public record PlanCardResponse(String planCode, String displayName, String description, List<PlanBenefitResponse> benefits,
        List<PlanBillingOptionResponse> billingOptions, Long version, String saleStatus,
        Long speakingDurationDailyLimitSeconds) {
    public PlanCardResponse { benefits = List.copyOf(benefits); billingOptions = List.copyOf(billingOptions); }
    public PlanCardResponse(String planCode, String displayName, String description, List<PlanBenefitResponse> benefits,
            List<PlanBillingOptionResponse> billingOptions, Long version, String saleStatus) {
        this(planCode, displayName, description, benefits, billingOptions, version, saleStatus, null);
    }
    public PlanCardResponse(String planCode, String displayName, String description, List<PlanBenefitResponse> benefits,
            List<PlanBillingOptionResponse> billingOptions) {
        this(planCode, displayName, description, benefits, billingOptions, null, null, null);
    }
}
