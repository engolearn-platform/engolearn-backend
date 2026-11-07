package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.subscription.contract.SubscriptionAccountInitialization;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.UserProfile;
import fit.iuh.engolearn.user.repository.UserRegistrationRepository;
import fit.iuh.engolearn.user.service.UserRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class UserRegistrationServiceImpl implements UserRegistrationService {
    private final UserRegistrationRepository registrations;
    private final SubscriptionAccountInitialization subscriptions;

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public User register(User user) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("MongoDB transactions must be enabled for account registration");
        }
        User saved = registrations.insertUser(user);
        registrations.insertProfile(UserProfile.builder()
                .id(saved.getId())
                .cefrLevel(CefrLevel.A1)
                .build());
        try {
            subscriptions.initializeNewAccount(saved.getId());
        } catch (SubscriptionUnavailableException failure) {
            throw new AuthFailure("Account setup is temporarily unavailable", "ACCOUNT_SETUP_UNAVAILABLE", 503);
        }
        return saved;
    }
}
