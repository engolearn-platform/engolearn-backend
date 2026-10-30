package fit.iuh.engolearn.subscription.service;
import java.time.Instant;
public interface SubscriptionEntitlementUpdates {
    void refreshOwned(String userId, Instant at);
}
