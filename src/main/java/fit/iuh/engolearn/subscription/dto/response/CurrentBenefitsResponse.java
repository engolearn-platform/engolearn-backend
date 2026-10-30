package fit.iuh.engolearn.subscription.dto.response;
import java.util.List;
public record CurrentBenefitsResponse(String planCode, String displayName, String policySnapshotId, List<PlanBenefitResponse> benefits) {
    public CurrentBenefitsResponse { benefits = List.copyOf(benefits); }
}
