package fit.iuh.engolearn.subscription.contract;

import java.time.Instant;

public interface SubscriptionManagementSummaryProvider {
    long countActivePaidPremiumUsersAt(Instant at);
}
