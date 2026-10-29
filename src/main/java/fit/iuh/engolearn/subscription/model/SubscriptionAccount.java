package fit.iuh.engolearn.subscription.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("subscription_accounts")
public record SubscriptionAccount(@Id String userId, boolean trialUsed, long entitlementVersion, long revision, String provenance) {}
