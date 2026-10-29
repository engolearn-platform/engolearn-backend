package fit.iuh.engolearn.subscription.dto.response;
import java.util.List;
public record CurrentBenefits(String planCode, String displayName, String policySnapshotId, List<PlanBenefit> benefits) {
    public CurrentBenefits { benefits = List.copyOf(benefits); }
}
