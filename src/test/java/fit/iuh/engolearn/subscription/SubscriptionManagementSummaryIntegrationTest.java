package fit.iuh.engolearn.subscription;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.model.SubscriptionPeriod;
import fit.iuh.engolearn.subscription.model.enums.PeriodKind;
import fit.iuh.engolearn.subscription.model.enums.PeriodState;
import fit.iuh.engolearn.subscription.repository.SubscriptionRepository;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfDockerAvailable
class SubscriptionManagementSummaryIntegrationTest extends AbstractMongoIntegrationTest {
    private static final Instant AT = Instant.parse("2026-11-28T12:00:00Z");

    @Autowired private SubscriptionRepository subscriptions;

    @Test
    void countsOnlyUniquePaidPeriodsEffectiveAtTheInclusiveStartExclusiveEndInstant() {
        save("paid", "u1", PeriodKind.MONTHLY, PeriodState.ACTIVE, AT, AT.plusSeconds(60));
        save("trial", "u2", PeriodKind.TRIAL, PeriodState.ACTIVE, AT.minusSeconds(1), AT.plusSeconds(60));
        save("expired", "u3", PeriodKind.ANNUAL, PeriodState.ACTIVE, AT.minusSeconds(60), AT);
        save("refunded", "u4", PeriodKind.ANNUAL, PeriodState.REFUNDED, AT.minusSeconds(1), AT.plusSeconds(60));
        save("future", "u5", PeriodKind.ANNUAL, PeriodState.ACTIVE, AT.plusSeconds(1), AT.plusSeconds(60));
        mongoTemplate.getCollection("subscription_periods").insertOne(new Document("user_id", "invalid")
                .append("kind", "UNKNOWN").append("state", "ACTIVE")
                .append("period_start_at", java.util.Date.from(AT.minusSeconds(1)))
                .append("period_end_at", java.util.Date.from(AT.plusSeconds(60))));

        assertThat(subscriptions.countActivePaidPremiumUsersAt(AT)).isEqualTo(1);
        assertThat(mongoTemplate.findById("paid", SubscriptionPeriod.class).effectiveAt(AT)).isTrue();
        assertThat(mongoTemplate.findById("expired", SubscriptionPeriod.class).effectiveAt(AT)).isFalse();
    }

    @Test
    void returnsZeroWhenNoPeriodIsEffectiveAndRejectsOverlappingEffectivePeriods() {
        assertThat(subscriptions.countActivePaidPremiumUsersAt(AT)).isZero();
        save("paid", "overlap", PeriodKind.MONTHLY, PeriodState.ACTIVE, AT.minusSeconds(1), AT.plusSeconds(60));
        save("trial", "overlap", PeriodKind.TRIAL, PeriodState.ACTIVE, AT.minusSeconds(1), AT.plusSeconds(60));

        assertThatThrownBy(() -> subscriptions.countActivePaidPremiumUsersAt(AT))
                .isInstanceOf(SubscriptionUnavailableException.class);
    }

    private void save(String id, String userId, PeriodKind kind, PeriodState state, Instant start, Instant end) {
        mongoTemplate.getCollection("subscription_periods").insertOne(new Document("_id", id)
                .append("user_id", userId).append("kind", kind.name()).append("state", state.name())
                .append("period_start_at", java.util.Date.from(start)).append("period_end_at", java.util.Date.from(end)));
    }
}
