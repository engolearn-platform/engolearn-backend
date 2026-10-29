package fit.iuh.engolearn.subscription.model;
import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshot;
import fit.iuh.engolearn.subscription.dto.response.CurrentBenefits;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_quota_policy_versions")
public record PolicyVersion(@Id String id, String scope, long version, Instant effectiveAt,
    QuotaPolicySnapshot snapshot, CurrentBenefits benefits) {}
