package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.subscription.contract.SubscriptionAccountInitialization;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.mapper.SubscriptionModelMapper;
import fit.iuh.engolearn.subscription.model.SubscriptionAccount;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionAccountInitializationImpl implements SubscriptionAccountInitialization {
    private final SubscriptionRepository subscriptions;

    @Override
    @Transactional(transactionManager = "mongoTransactionManager", propagation = Propagation.MANDATORY)
    public void initializeNewAccount(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User identity is required");
        }
        try {
            SubscriptionAccount existing = subscriptions.findAccount(userId);
            if (existing != null) {
                if (existing.getProvenance() == null) {
                    throw new SubscriptionUnavailableException();
                }
                return;
            }
            subscriptions.initializeAccountHistory(
                    SubscriptionModelMapper.subscriptionAccount(userId, false, 0, 0, "NEW_ACCOUNT"));
        } catch (RuntimeException failure) {
            throw new SubscriptionUnavailableException(failure);
        }
    }
}
