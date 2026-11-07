package fit.iuh.engolearn.user.service.impl;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.enums.UserRole;
import fit.iuh.engolearn.subscription.contract.SubscriptionAccountInitialization;
import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshotDto;
import fit.iuh.engolearn.subscription.contract.dto.QuotaRuleDto;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;
import fit.iuh.engolearn.subscription.dto.response.CurrentBenefitsResponse;
import fit.iuh.engolearn.subscription.model.SubscriptionAccount;
import fit.iuh.engolearn.subscription.model.enums.AccountProvenance;
import fit.iuh.engolearn.subscription.service.SubscriptionPublicationService;
import fit.iuh.engolearn.subscription.service.SubscriptionService;
import fit.iuh.engolearn.quota.service.QuotaService;
import fit.iuh.engolearn.user.model.User;
import fit.iuh.engolearn.user.model.UserProfile;
import fit.iuh.engolearn.user.service.UserRegistrationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfDockerAvailable
@TestPropertySource(properties = {
        "engolearn.security.jwt.secret=registration-test-secret-at-least-32-bytes",
        "engolearn.subscription.activation-delay-ms=3600000"
})
class UserRegistrationIntegrationTest extends AbstractMongoIntegrationTest {
    @Autowired private UserRegistrationService registration;
    @Autowired private SubscriptionAccountInitialization initialization;
    @Autowired private SubscriptionPublicationService publication;
    @Autowired private SubscriptionService subscriptions;
    @Autowired private QuotaService quota;
    @Autowired @Qualifier("mongoTransactionManager") private PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepareCollectionsAndPolicies() {
        mongoTemplate.createCollection(User.class);
        mongoTemplate.createCollection(UserProfile.class);
        mongoTemplate.createCollection(SubscriptionAccount.class);
        publication.publish(new PublicationManifestRequest("registration-test", "isolated-test", List.of(),
                List.of(policy("FREE"), policy("TRIAL")), List.of()));
    }

    private PublicationManifestRequest.PolicyRequest policy(String scope) {
        String id = "registration-" + scope;
        return new PublicationManifestRequest.PolicyRequest(id, scope, 1, Instant.parse("2026-01-01T00:00:00Z"),
                new QuotaPolicySnapshotDto(id, scope, 1,
                        List.of(new QuotaRuleDto("TEST", "Test quota", 3, "ATTEMPT", "MONTHLY", 0))),
                new CurrentBenefitsResponse(scope.equals("FREE") ? "FREE" : "PREMIUM", scope, id, List.of()));
    }

    private User newUser(String id) {
        return new User(id, id + "@example.com", "test-hash", UserRole.USER, false);
    }

    @Test
    void registrationMakesFreeSubscriptionAndZeroUsageImmediatelyAvailable() {
        User user = registration.register(newUser("new-user"));

        assertThat(mongoTemplate.findById(user.getId(), UserProfile.class)).isNotNull();
        SubscriptionAccount account = mongoTemplate.findById(user.getId(), SubscriptionAccount.class);
        assertThat(account.getProvenance()).isEqualTo(AccountProvenance.NEW_ACCOUNT);
        assertThat(account.isTrialUsed()).isFalse();
        assertThat(subscriptions.summary(user.getId()).tier()).isEqualTo("FREE");
        assertThat(subscriptions.summary(user.getId()).trialEligible()).isTrue();
        assertThat(quota.usage(user.getId()).items()).singleElement().satisfies(item -> {
            assertThat(item.used()).isZero();
            assertThat(item.remaining()).isEqualTo(3);
        });
    }

    @Test
    void subscriptionFailureRollsBackUserAndProfile() {
        mongoTemplate.insert(SubscriptionAccount.builder().id("invalid-history").build());

        assertThatThrownBy(() -> registration.register(newUser("invalid-history")))
                .isInstanceOf(AuthFailure.class)
                .hasMessageContaining("temporarily unavailable");
        assertThat(mongoTemplate.findById("invalid-history", User.class)).isNull();
        assertThat(mongoTemplate.findById("invalid-history", UserProfile.class)).isNull();
    }

    @Test
    void repeatedInitializationPreservesUsedTrialAndRevisions() {
        User user = registration.register(newUser("existing-user"));
        SubscriptionAccount account = mongoTemplate.findById(user.getId(), SubscriptionAccount.class);
        account.setTrialUsed(true);
        account.setRevision(4);
        account.setEntitlementVersion(2);
        mongoTemplate.save(account);

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> initialization.initializeNewAccount(user.getId()));

        SubscriptionAccount unchanged = mongoTemplate.findById(user.getId(), SubscriptionAccount.class);
        assertThat(unchanged.isTrialUsed()).isTrue();
        assertThat(unchanged.getRevision()).isEqualTo(4);
        assertThat(unchanged.getEntitlementVersion()).isEqualTo(2);
    }
}
