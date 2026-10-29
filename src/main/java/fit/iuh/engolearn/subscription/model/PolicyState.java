package fit.iuh.engolearn.subscription.model;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_policy_state")
public record PolicyState(@Id String scope, String activeVersionId, String pendingVersionId,
    Instant pendingEffectiveAt, long activationSequence, long revision, String manifestFingerprint) {}
