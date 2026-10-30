package fit.iuh.engolearn.subscription;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.shared.messaging.*;
import fit.iuh.engolearn.shared.messaging.model.OutboxDocument;
import fit.iuh.engolearn.shared.messaging.repository.DurableMessageRepository;
import fit.iuh.engolearn.payment.contract.event.PaymentSucceededEvent;
import fit.iuh.engolearn.payment.model.*;
import fit.iuh.engolearn.payment.model.enums.RefundStatus;
import fit.iuh.engolearn.quota.contract.*;
import fit.iuh.engolearn.quota.contract.dto.UsageCommandDto;
import fit.iuh.engolearn.quota.infra.event.SubscriptionQuotaMessageConsumer;
import fit.iuh.engolearn.quota.model.QuotaEntitlementProjection;
import fit.iuh.engolearn.quota.repository.QuotaMessageRepository;
import fit.iuh.engolearn.quota.repository.QuotaRepository;
import fit.iuh.engolearn.subscription.contract.EffectiveEntitlementProvider;
import fit.iuh.engolearn.subscription.contract.dto.*;
import fit.iuh.engolearn.subscription.dto.request.PublicationManifestRequest;
import fit.iuh.engolearn.subscription.dto.response.*;
import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionMessageRepository;
import fit.iuh.engolearn.subscription.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfDockerAvailable
@Import(DurableMessagingIntegrationTest.TimeConfig.class)
@TestPropertySource(properties = {"engolearn.messaging.enabled=false", "engolearn.subscription.activation-delay-ms=3600000"})
class DurableMessagingIntegrationTest extends AbstractMongoIntegrationTest {
    static final Instant START = Instant.parse("2026-10-28T12:00:00Z");
    static class MutableClock extends Clock {
        volatile Instant now = START;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock deliveryClock() { return new MutableClock(); }
    }
    @Autowired MutableClock clock;
    @Autowired List<DurableMessageRepository> repositories;
    @Autowired SubscriptionMessageRepository outbox;
    @MockitoSpyBean QuotaMessageRepository receipts;
    @Autowired SubscriptionQuotaMessageConsumer consumer;
    @Autowired DurableMessageDispatcher dispatcher;
    @Autowired SubscriptionTransactions transactions;
    @Autowired SubscriptionPublicationService publication;
    @Autowired SubscriptionActivationService activation;
    @Autowired SubscriptionService subscriptions;
    @Autowired EffectiveEntitlementProvider provider;
    @Autowired QuotaConsumption consumption;
    @Autowired QuotaAvailability availability;
    @Autowired QuotaRepository quotaStore;

    @BeforeEach void fixtures() {
        clock.now = START;
        repositories.forEach(DurableMessageRepository::ensureStorage);
        quotaStore.ensureIndexes();
        publication.publish(manifest("initial", List.of(policy("free1", "FREE", 1, START.minusSeconds(1), 2),
                policy("trial1", "TRIAL", 1, START.minusSeconds(1), 5)), true));
    }
    PublicationManifestRequest.PolicyRequest policy(String id, String scope, long version, Instant at, long limit) {
        return new PublicationManifestRequest.PolicyRequest(id, scope, version, at,
                new QuotaPolicySnapshotDto(id, scope, version, List.of(new QuotaRuleDto("TEST", "Test", limit, "ATTEMPT", "MONTHLY", 0))),
                new CurrentBenefitsResponse(scope.equals("FREE") ? "FREE" : "PREMIUM", scope, id,
                        List.of(new PlanBenefitResponse("TEST", "Test", "Test benefit", null, 0))));
    }
    PublicationManifestRequest manifest(String id, List<PublicationManifestRequest.PolicyRequest> policies, boolean account) {
        return new PublicationManifestRequest(id, "isolated-test", account ? List.of(new PublicationManifestRequest.PlanRequest(
                "free-offer", "FREE", 1, START.minusSeconds(1), true, 0,
                new PlanCardResponse("FREE", "Free", null, List.of(), List.of()))) : List.of(), policies,
                account ? List.of(new PublicationManifestRequest.AccountHistoryRequest("u", false, "NEW_ACCOUNT")) : List.of());
    }
    long count(String name) { return mongoTemplate.getCollection(name).countDocuments(); }
    OutboxDocument claim() { return outbox.claim(Set.of(consumer.recipient()), clock.instant(), clock.instant().plusSeconds(30)); }

    @Test void producerRollbackKeepsTrialAccountReceiptAndOutboxAtomic() {
        assertThatThrownBy(() -> transactions.execute(() -> {
            subscriptions.activateTrial("u", UUID.randomUUID().toString());
            throw new IllegalStateException("rollback after enqueue");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(mongoTemplate.findById("u", SubscriptionAccount.class).isTrialUsed()).isFalse();
        assertThat(count("subscription_periods")).isZero();
        assertThat(count("subscription_operation_receipts")).isZero();
        assertThat(count("subscription_outbox")).isZero();
        subscriptions.activateTrial("u", UUID.randomUUID().toString());
        assertThat(count("subscription_outbox")).isEqualTo(1);
        assertThat(count("quota_entitlement_projection")).isZero();
    }

    @Test void lostAcknowledgmentRedeliversAfterLeaseWithoutResettingUsage() {
        assertThat(consumption.consume(new UsageCommandDto("u", "TEST", 1, "TEST", "usage-1", "r", null)).accepted()).isTrue();
        dispatcher.dispatch();
        subscriptions.activateTrial("u", UUID.randomUUID().toString());
        OutboxDocument delivery = claim();
        consumer.consume(delivery.envelope());
        long receiptCount = count("quota_message_receipts");
        assertThat(availability.checkAvailability("u", "TEST").used()).isEqualTo(1);
        assertThat(availability.checkAvailability("u", "TEST").limit()).isEqualTo(5);
        clock.now = START.plusSeconds(31);
        assertThat(dispatcher.dispatch()).isEqualTo(1);
        assertThat(count("quota_message_receipts")).isEqualTo(receiptCount);
        assertThat(availability.checkAvailability("u", "TEST").used()).isEqualTo(1);
        assertThat(count("quota_usage_ledger")).isEqualTo(1);
    }

    @Test void recipientClaimsAndLeaseTokensFenceStaleWorkers() {
        subscriptions.activateTrial("u", UUID.randomUUID().toString());
        String id = UUID.randomUUID().toString();
        transactions.execute(() -> {
            outbox.append(new MessageEnvelopeDto(id, "CREATE_CHECKOUT", 1, "purchase", "payment.checkout", START, "{}"));
            return null;
        });
        OutboxDocument first = outbox.claim(Set.of("payment.checkout"), START, START.plusSeconds(30));
        assertThat(first.getMessageId()).isEqualTo(id);
        assertThat(outbox.claim(Set.of("payment.checkout"), START, START.plusSeconds(30))).isNull();
        OutboxDocument replacement = outbox.claim(Set.of("payment.checkout"), START.plusSeconds(31), START.plusSeconds(61));
        assertThat(replacement.getLeaseToken()).isNotEqualTo(first.getLeaseToken());
        assertThat(outbox.acknowledge(first, START.plusSeconds(31))).isFalse();
        assertThat(outbox.acknowledge(replacement, START.plusSeconds(31))).isTrue();
        assertThat(dispatcher.dispatch()).isEqualTo(1);
        assertThat(count("quota_message_receipts")).isEqualTo(1);
    }

    @Test void consumerEffectAndReceiptRollbackTogetherBeforeSafeRetry() {
        subscriptions.activateTrial("u", UUID.randomUUID().toString());
        OutboxDocument delivery = claim();
        doThrow(new IllegalStateException("injected receipt failure")).when(receipts).recordReceipt(any(), any());
        assertThatThrownBy(() -> consumer.consume(delivery.envelope())).isInstanceOf(IllegalStateException.class);
        assertThat(count("quota_entitlement_projection")).isZero();
        assertThat(count("quota_message_receipts")).isZero();
        reset(receipts);
        consumer.consume(delivery.envelope());
        assertThat(count("quota_entitlement_projection")).isEqualTo(1);
        assertThat(count("quota_message_receipts")).isEqualTo(1);
    }

    @RepeatedTest(2) void concurrentSameMessageCommitsOneReceiptAndRejectsChangedPayload() throws Exception {
        subscriptions.activateTrial("u", UUID.randomUUID().toString());
        MessageEnvelopeDto message = claim().envelope();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> action = () -> {
                start.await(10, TimeUnit.SECONDS);
                try { consumer.consume(message); return true; }
                catch (org.springframework.dao.DataAccessException | com.mongodb.MongoException contention) { return false; }
            };
            Future<Boolean> first = pool.submit(action), second = pool.submit(action);
            start.countDown();
            boolean a = first.get(20, TimeUnit.SECONDS), b = second.get(20, TimeUnit.SECONDS);
            assertThat(a || b).isTrue();
            consumer.consume(message);
            assertThat(count("quota_message_receipts")).isEqualTo(1);
            assertThat(count("quota_entitlement_projection")).isEqualTo(1);
            MessageEnvelopeDto conflicting = new MessageEnvelopeDto(message.messageId(), message.messageType(), 1,
                    "different-user", message.recipient(), message.occurredAt(), message.payload());
            assertThatThrownBy(() -> consumer.consume(conflicting)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conflicting payload");
        } finally { pool.shutdownNow(); }
    }

    @Test void legacyVersionsAdvanceOnceAndExpiredRightsCannotBeRewoundByOldDelivery() {
        SubscriptionAccount account = mongoTemplate.findById("u", SubscriptionAccount.class);
        account.setEntitlementVersion(12);
        mongoTemplate.save(account);
        assertThat(provider.resolveEffectiveEntitlement("u", START).entitlementVersion()).isEqualTo(13);
        assertThat(provider.resolveEffectiveEntitlement("u", START).entitlementVersion()).isEqualTo(13);
        dispatcher.dispatch();
        var trial = subscriptions.activateTrial("u", UUID.randomUUID().toString()).activation();
        OutboxDocument old = claim();
        clock.now = trial.periodEndAt();
        assertThat(provider.resolveEffectiveEntitlement("u", clock.instant()).entitlementVersion()).isEqualTo(15);
        dispatcher.dispatch();
        consumer.consume(old.envelope());
        assertThat(availability.checkAvailability("u", "TEST").limit()).isEqualTo(2);
        assertThat(mongoTemplate.findAll(QuotaEntitlementProjection.class).getFirst().getEntitlementVersion()).isEqualTo(15);
    }

    @Test void scheduledFreeActivationSurvivesDispatchGapAndPreservesHistory() {
        consumption.consume(new UsageCommandDto("u", "TEST", 1, "TEST", "usage-before", "r", null));
        dispatcher.dispatch();
        Instant midnight = START.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        publication.publish(manifest("update", List.of(policy("free2", "FREE", 2, midnight, 7)), false));
        clock.now = midnight.plusSeconds(60);
        activation.activateDue();
        assertThat(count("subscription_outbox")).isEqualTo(2);
        dispatcher.dispatch();
        assertThat(availability.checkAvailability("u", "TEST").limit()).isEqualTo(7);
        assertThat(availability.checkAvailability("u", "TEST").used()).isZero();
        assertThat(count("quota_usage_ledger")).isEqualTo(1);
        activation.activateDue();
        assertThat(dispatcher.dispatch()).isZero();
        assertThat(availability.checkAvailability("u", "TEST").used()).isZero();
    }

    @Test void businessIndexesPreventDuplicatePendingPurchasePaymentAndFullRefund() {
        mongoTemplate.insert(PremiumPurchase.builder().id("p1").purchaseReference("ref1").userId("u")
                .idempotencyKey("key1").blocksNewPurchase(true).build());
        assertThatThrownBy(() -> mongoTemplate.insert(PremiumPurchase.builder().id("p2").purchaseReference("ref2")
                .userId("u").idempotencyKey("key2").blocksNewPurchase(true).build())).isInstanceOf(DuplicateKeyException.class);
        mongoTemplate.insert(PaymentTransaction.builder().id("pay1").purchaseReference("ref1").build());
        assertThatThrownBy(() -> mongoTemplate.insert(PaymentTransaction.builder().id("pay2").purchaseReference("ref1").build()))
                .isInstanceOf(DuplicateKeyException.class);
        RefundTransaction pending = mongoTemplate.insert(RefundTransaction.builder().id("r1").refundReference("rr1")
                .paymentId("pay1").status(RefundStatus.PROCESSING).blocksNewRefund(true).build());
        assertThatThrownBy(() -> mongoTemplate.insert(RefundTransaction.builder().id("r2").refundReference("rr2")
                .paymentId("pay1").status(RefundStatus.REQUESTED).blocksNewRefund(true).build())).isInstanceOf(DuplicateKeyException.class);
        pending.setBlocksNewRefund(false);
        pending.setStatus(RefundStatus.SUCCEEDED);
        mongoTemplate.save(pending);
        assertThatThrownBy(() -> mongoTemplate.insert(RefundTransaction.builder().id("r3").refundReference("rr3")
                .paymentId("pay1").status(RefundStatus.SUCCEEDED).build())).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> new PaymentSucceededEvent("event", 1, START, "pay", "purchase", "u", 10, "USD", START))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("VND");
    }
}
