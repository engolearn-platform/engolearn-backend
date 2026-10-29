package fit.iuh.engolearn.subscription.dto.request;
import fit.iuh.engolearn.subscription.model.PlanVersion;
import fit.iuh.engolearn.subscription.model.PolicyVersion;
import java.util.List;
public record PublicationManifest(String manifestId, String environment, List<PlanVersion> plans,
    List<PolicyVersion> policies, List<AccountHistory> accounts) {
    public record AccountHistory(String userId, boolean trialUsed, String provenance) {}
}
