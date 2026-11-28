package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.contract.SubscriptionManagementSummaryProvider;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionManagementSummaryProviderImpl implements SubscriptionManagementSummaryProvider {
    private final SubscriptionRepository subscriptions;

    @Override
    public long countActivePaidPremiumUsersAt(Instant at) {
        return subscriptions.countActivePaidPremiumUsersAt(at);
    }
}
