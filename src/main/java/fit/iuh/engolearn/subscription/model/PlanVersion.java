package fit.iuh.engolearn.subscription.model;
import fit.iuh.engolearn.subscription.dto.response.PlanCard;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_plan_versions")
public record PlanVersion(@Id String id, String scope, long version, Instant effectiveAt, boolean published,
    int displayOrder, PlanCard plan) {}
