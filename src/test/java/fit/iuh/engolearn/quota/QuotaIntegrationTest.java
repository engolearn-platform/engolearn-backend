package fit.iuh.engolearn.quota;

import fit.iuh.engolearn.config.AbstractMongoIntegrationTest;
import fit.iuh.engolearn.quota.contract.*;
import fit.iuh.engolearn.quota.contract.dto.*;
import fit.iuh.engolearn.quota.repository.QuotaStore;
import fit.iuh.engolearn.quota.service.*;
import fit.iuh.engolearn.subscription.contract.*;
import fit.iuh.engolearn.subscription.contract.dto.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static fit.iuh.engolearn.quota.repository.QuotaStore.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfDockerAvailable
@Import(QuotaIntegrationTest.Fixtures.class)
@TestPropertySource(properties = "engolearn.subscription.activation-delay-ms=3600000")
class QuotaIntegrationTest extends AbstractMongoIntegrationTest {
    private static final Instant START = Instant.parse("2026-10-28T12:00:00Z");
    private static final String SPEAKING = "SPEAKING_DURATION";
    private static final String ATTEMPT = "PROFICIENCY_CHECK_ATTEMPT";
    private static final String WEEKLY = "WEEKLY_TEST";

    static class TestClock extends Clock {
        volatile Instant at = START;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return at; }
    }

    static class TestEntitlements implements EffectiveEntitlementProvider {
        final Map<String, EffectiveEntitlement> users = new ConcurrentHashMap<>();
        volatile boolean fail;
        volatile java.util.function.Consumer<String> onLookup = user -> {};
        public EffectiveEntitlement resolveEffectiveEntitlement(String userId, Instant at) {
            onLookup.accept(userId);
            if (fail || !users.containsKey(userId)) throw new SubscriptionUnavailableException();
            return users.get(userId);
        }
    }

    @TestConfiguration
    static class Fixtures {
        @Bean @Primary TestClock quotaTestClock() { return new TestClock(); }
        @Bean @Primary TestEntitlements quotaTestEntitlements() { return new TestEntitlements(); }
    }

    @Autowired QuotaConsumption consumption;
    @Autowired QuotaUsageRecording recording;
    @Autowired QuotaAvailability availability;
    @Autowired QuotaService service;
    @Autowired QuotaEntitlements projections;
    @Autowired QuotaActivationHandler activations;
    @Autowired QuotaStore store;
    @Autowired MongoTransactionManager manager;
    @Autowired TestClock clock;
    @Autowired TestEntitlements provider;

    @BeforeEach
    void setup() {
        clock.at = START;
        provider.fail = false;
        provider.onLookup = user -> {};
        provider.users.clear();
        provider.users.put("u", entitlement("u", 0, snapshot("FREE", 1, 10, 2), 0, START.minusSeconds(1), null));
        store.ensureIndexes();
        mongoTemplate.createCollection("test_attempts");
        mongoTemplate.createCollection("test_timers");
        mongoTemplate.createCollection("test_preconditions");
    }

    private QuotaPolicySnapshot snapshot(String scope, long version, long seconds, long attempts) {
        return new QuotaPolicySnapshot(scope + version, scope, version, List.of(
                new QuotaRule(SPEAKING, "Speaking", seconds, "SECOND", "DAILY", 0),
                new QuotaRule(ATTEMPT, "Assessment", attempts, "ATTEMPT", "MONTHLY", 1),
                new QuotaRule(WEEKLY, "Weekly", 5, "ATTEMPT", "WEEKLY", 2)));
    }

    private EffectiveEntitlement entitlement(String user, long revision, QuotaPolicySnapshot policy, long sequence, Instant effective, Instant until) {
        return new EffectiveEntitlement(user, revision, policy.policyScope(), policy.policySnapshotId(), policy.policyVersion(), sequence, effective, until, policy);
    }

    private UsageCommand command(String type, long amount, String key) {
        return new UsageCommand("u", type, amount, "TEST_CALLER", key, "reference-" + key, null);
    }

    private long count(String collection) { return mongoTemplate.getCollection(collection).countDocuments(); }
    private Document counter(String type) { return store.one(COUNTERS, Criteria.where("userId").is("u").and("quotaType").is(type)); }
    private void publishEntitlement(EffectiveEntitlement entitlement) {
        provider.users.put(entitlement.userId(), entitlement);
        projections.onEntitlementChanged(new EntitlementChanged(entitlement));
    }

    @Test
    void knownAmountDenialHasNoLedgerAndReadChecksDoNotReserve() {
        Document before = counter(SPEAKING);
        assertThat(availability.checkAvailability("u", SPEAKING).remaining()).isEqualTo(10);
        assertThat(counter(SPEAKING)).isEqualTo(before);
        assertThat(count(LEDGER)).isZero();
        UsageResult accepted = consumption.consume(command(SPEAKING, 8, "accepted"));
        assertThat(accepted.accepted()).isTrue();
        assertThat(consumption.consume(command(SPEAKING, 3, "denied")).accepted()).isFalse();
        assertThat(count(LEDGER)).isEqualTo(1);
        Document original = new Document(counter(SPEAKING));
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(8);
        assertThat(service.usage("u").items()).hasSize(3);
        assertThat(counter(SPEAKING)).isEqualTo(original);
        assertThat(count(LEDGER)).isEqualTo(1);
    }

    @Test
    void concurrentFirstCounterCreationAndConditionalExistingCounterNeverExceedCap() throws Exception {
        provider.users.put("u", entitlement("u", 0, snapshot("FREE", 1, 1, 1), 0, START.minusSeconds(1), null));
        assertBoundedConcurrentAcceptance("first");
        assertThat(counter(SPEAKING).getLong("usedAmount")).isEqualTo(1);
        assertThat(count(COUNTERS)).isEqualTo(1);
        assertThat(count(LEDGER)).isEqualTo(1);
        clock.at = START.plus(Duration.ofDays(1));
        assertBoundedConcurrentAcceptance("existing");
        assertThat(counter(SPEAKING).getLong("usedAmount")).isEqualTo(1);
        assertThat(count(COUNTERS)).isEqualTo(1);
        assertThat(count(LEDGER)).isEqualTo(2);
    }

    private void assertBoundedConcurrentAcceptance(String prefix) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UsageResult>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                String key = prefix + index;
                results.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return consumption.consume(command(SPEAKING, 1, key));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int accepted = 0;
            for (Future<UsageResult> result : results) if (result.get(20, TimeUnit.SECONDS).accepted()) accepted++;
            assertThat(accepted).isEqualTo(1);
        } finally { workers.shutdownNow(); }
    }

    @Test
    void acceptedReplayRetainsOriginalOutcomeAcrossMidnightAndExpiryAndRejectsChangedPayload() {
        provider.users.put("u", entitlement("u", 1, snapshot("TRIAL", 1, 10, 2), 0, START.minusSeconds(1), START.plusSeconds(60)));
        UsageCommand command = new UsageCommand("u", SPEAKING, 6, "SPEAKING", "turn-1", "turn", START.minusNanos(1));
        UsageResult original = recording.recordUsage(command);
        clock.at = Instant.parse("2026-10-29T17:00:00Z");
        provider.fail = true;
        assertThat(recording.recordUsage(command)).isEqualTo(original);
        assertThatThrownBy(() -> recording.recordUsage(new UsageCommand("u", SPEAKING, 7, "SPEAKING", "turn-1", "turn", command.occurredAt())))
                .isInstanceOf(QuotaIdempotencyConflictException.class);
        assertThatThrownBy(() -> consumption.consume(command)).isInstanceOf(QuotaIdempotencyConflictException.class);
        assertThat(count(LEDGER)).isEqualTo(1);
    }

    @Test
    void speakingCallerRecordsWholeTurnAtAcceptanceWithTruthfulOverrunAndStableTurnKey() {
        clock.at = Instant.parse("2026-10-28T16:59:00Z");
        Instant turnStarted = clock.at;
        assertThat(availability.checkAvailability("u", SPEAKING).remaining()).isEqualTo(10);
        assertThat(count(COUNTERS)).isZero();
        clock.at = Instant.parse("2026-10-28T17:01:00Z");
        UsageCommand turn = new UsageCommand("u", SPEAKING, 25, "SPEAKING", "audio-turn-1", "turn-1", turnStarted);
        UsageResult first = recording.recordUsage(turn);
        assertThat(first.availability().windowStartAt()).isEqualTo(Instant.parse("2026-10-28T17:00:00Z"));
        assertThat(first.availability().used()).isEqualTo(25);
        assertThat(first.availability().status()).isEqualTo("OVER_LIMIT");
        assertThat(availability.checkAvailability("u", SPEAKING).remaining()).isZero();
        assertThat(service.usage("u").items().getFirst().overLimitAmount()).isEqualTo(15);
        clock.at = clock.at.plus(Duration.ofDays(1));
        Document beforeRead = new Document(counter(SPEAKING));
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isZero();
        assertThat(counter(SPEAKING)).isEqualTo(beforeRead);
        assertThat(recording.recordUsage(turn)).isEqualTo(first);
        assertThat(count(LEDGER)).isEqualTo(1);
    }

    @Test
    void callerOwnsAttemptTimerAndQuotaAtomicityIncludingDenialFailureAndAcceptedRetry() {
        TestAssessmentCaller caller = new TestAssessmentCaller();
        String first = caller.start("start-1", false);
        assertThat(caller.start("start-1", false)).isEqualTo(first);
        assertThat(count("test_attempts")).isEqualTo(1);
        assertThat(count("test_timers")).isEqualTo(1);
        assertThat(count(LEDGER)).isEqualTo(1);
        assertThat(counter(ATTEMPT).getLong("usedAmount")).isEqualTo(1);
        assertThatThrownBy(() -> caller.start("failed", true)).isInstanceOf(IllegalStateException.class).hasMessage("injected after consume");
        assertThat(count("test_attempts")).isEqualTo(1);
        assertThat(count("test_timers")).isEqualTo(1);
        assertThat(count(LEDGER)).isEqualTo(1);
        assertThat(counter(ATTEMPT).getLong("usedAmount")).isEqualTo(1);
        caller.start("start-2", false);
        assertThatThrownBy(() -> caller.start("denied", false)).isInstanceOf(QuotaDenied.class);
        assertThat(count("test_attempts")).isEqualTo(2);
        assertThat(count("test_timers")).isEqualTo(2);
        assertThat(count(LEDGER)).isEqualTo(2);
        assertThat(counter(ATTEMPT).getLong("usedAmount")).isEqualTo(2);
    }

    private class TestAssessmentCaller {
        String start(String intent, boolean fail) {
            String preparedResource = "prepared-before-transaction";
            return new TransactionTemplate(manager).execute(status -> {
                Document existing = mongoTemplate.findById(intent, Document.class, "test_attempts");
                if (existing != null) return existing.getString("attemptId");
                Document preconditions = mongoTemplate.findById("u", Document.class, "test_preconditions");
                if (preconditions != null && preconditions.getBoolean("blocked")) throw new IllegalStateException("Caller precondition");
                String attemptId = UUID.randomUUID().toString();
                mongoTemplate.insert(new Document("_id", intent).append("attemptId", attemptId).append("resource", preparedResource), "test_attempts");
                mongoTemplate.insert(new Document("_id", intent).append("attemptId", attemptId).append("startedAt", Date.from(clock.instant())), "test_timers");
                UsageResult result = consumption.consume(command(ATTEMPT, 1, intent));
                if (!result.accepted()) throw new QuotaDenied();
                if (fail) throw new IllegalStateException("injected after consume");
                return attemptId;
            });
        }
    }
    private static class QuotaDenied extends RuntimeException {}

    @Test
    void failedFirstCallerTransactionLeavesNoCounterOrLedgerAndCanRetrySameIntent() {
        TestAssessmentCaller caller = new TestAssessmentCaller();
        assertThatThrownBy(() -> caller.start("new", true)).isInstanceOf(IllegalStateException.class);
        assertThat(count(COUNTERS)).isZero();
        assertThat(count(LEDGER)).isZero();
        assertThat(count("test_attempts")).isZero();
        assertThat(count("test_timers")).isZero();
        assertThat(caller.start("new", false)).isNotBlank();
        assertThat(count(LEDGER)).isEqualTo(1);
    }

    @Test
    void missingAndExpiredProjectionsResolveAuthoritativelyButFailureNeverMeansZero() {
        assertThat(availability.checkAvailability("u", SPEAKING).limit()).isEqualTo(10);
        publishEntitlement(entitlement("u", 1, snapshot("TRIAL", 1, 20, 4), 0, START, START.plusSeconds(30)));
        assertThat(availability.checkAvailability("u", SPEAKING).limit()).isEqualTo(20);
        clock.at = START.plusSeconds(30);
        provider.users.put("u", entitlement("u", 2, snapshot("FREE", 1, 10, 2), 0, START, null));
        assertThat(availability.checkAvailability("u", SPEAKING).limit()).isEqualTo(10);
        provider.fail = true;
        assertThatThrownBy(() -> availability.checkAvailability("missing", SPEAKING)).isInstanceOf(QuotaUnavailableException.class);
        assertThat(count(COUNTERS)).isZero();
        assertThat(count(LEDGER)).isZero();
    }

    @Test
    void tierAndNewPeriodKeepUsageAndSnapshotsAndStaleEventCannotRewind() {
        UsageResult first = consumption.consume(command(SPEAKING, 7, "first"));
        publishEntitlement(entitlement("u", 1, snapshot("TRIAL", 1, 20, 4), 0, START, START.plusSeconds(60)));
        publishEntitlement(entitlement("u", 2, snapshot("PREMIUM", 1, 30, 6), 0, START, START.plusSeconds(120)));
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(7);
        assertThat(counter(SPEAKING).getString("allowanceId")).isEqualTo(first.allowanceId());
        projections.onEntitlementChanged(new EntitlementChanged(entitlement("u", 1, snapshot("TRIAL", 1, 20, 4), 0, START, START.plusSeconds(60))));
        assertThat(availability.checkAvailability("u", SPEAKING).limit()).isEqualTo(30);
        assertThatThrownBy(() -> publishEntitlement(entitlement("u", 2, snapshot("PREMIUM", 2, 40, 8), 0, START, START.plusSeconds(120))))
                .isInstanceOf(QuotaUnavailableException.class);
        assertThat(availability.checkAvailability("u", SPEAKING).limit()).isEqualTo(30);
    }

    @Test
    void equalEntitlementVersionAdvancesFreePolicyWithoutResetUntilDeliveredActivation() {
        consumption.consume(command(SPEAKING, 7, "first"));
        QuotaPolicySnapshot next = snapshot("FREE", 2, 5, 3);
        publishEntitlement(entitlement("u", 0, next, 1, START, null));
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(7);
        assertThat(availability.checkAvailability("u", SPEAKING).status()).isEqualTo("OVER_LIMIT");
        assertThat(count(RECEIPTS)).isZero();
        assertThatThrownBy(() -> publishEntitlement(entitlement("u", 0, snapshot("TRIAL", 1, 20, 4), 0, START, START.plusSeconds(60))))
                .isInstanceOf(QuotaUnavailableException.class);
    }

    @Test
    void lateFreeActivationResetsOnlyChangedCurrentFreeRulesPreservingPostEffectiveUsageAndHistory() {
        recording.recordUsage(command(SPEAKING, 8, "before"));
        consumption.consume(command(ATTEMPT, 1, "assessment"));
        provider.users.put("premium", entitlement("premium", 1, snapshot("PREMIUM", 1, 30, 5), 0, START, START.plus(Duration.ofDays(7))));
        recording.recordUsage(new UsageCommand("premium", SPEAKING, 6, "SPEAKING", "premium-turn", null, null));
        Instant effective = START.plusSeconds(3600);
        clock.at = effective.plusSeconds(10);
        QuotaPolicySnapshot next = snapshot("FREE", 2, 5, 2);
        provider.users.put("u", entitlement("u", 0, next, 1, effective, null));
        recording.recordUsage(command(SPEAKING, 3, "after"));
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(11);
        QuotaPolicyActivated event = new QuotaPolicyActivated("activation-1", "FREE", 2, 1, effective, Set.of(SPEAKING), next);
        activations.onPolicyActivated(event);
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(3);
        assertThat(availability.checkAvailability("u", SPEAKING).allowanceStartAt()).isEqualTo(effective);
        assertThat(availability.checkAvailability("u", ATTEMPT).used()).isEqualTo(1);
        assertThat(availability.checkAvailability("premium", SPEAKING).used()).isEqualTo(6);
        assertThat(count(LEDGER)).isEqualTo(4);
        assertThat(count(RECEIPTS)).isEqualTo(2);
        String allowance = counter(SPEAKING).getString("allowanceId");
        recording.recordUsage(command(SPEAKING, 1, "after-reset"));
        activations.onPolicyActivated(event);
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(4);
        assertThat(counter(SPEAKING).getString("allowanceId")).isEqualTo(allowance);
        activations.onPolicyActivated(new QuotaPolicyActivated("stale", "FREE", 1, 0, START, Set.of(SPEAKING), snapshot("FREE", 1, 10, 2)));
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(4);
    }

    @Test
    void activationAtCalendarBoundaryPreservesNewWindowUsageAndAllowance() {
        recording.recordUsage(command(SPEAKING, 6, "yesterday"));
        Instant midnight = Instant.parse("2026-10-28T17:00:00Z");
        clock.at = midnight;
        QuotaPolicySnapshot next = snapshot("FREE", 2, 20, 2);
        provider.users.put("u", entitlement("u", 0, next, 1, midnight, null));
        UsageResult first = recording.recordUsage(command(SPEAKING, 4, "today"));
        activations.onPolicyActivated(new QuotaPolicyActivated("midnight", "FREE", 2, 1, midnight, Set.of(SPEAKING), next));
        assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(4);
        assertThat(counter(SPEAKING).getString("allowanceId")).isEqualTo(first.allowanceId());
        assertThat(count(LEDGER)).isEqualTo(2);
    }

    @Test
    void delayedOldWindowActivationCannotEraseLaterWindowUsage() {
        recording.recordUsage(command(SPEAKING, 6, "yesterday"));
        Instant effective = START.plusSeconds(3600);
        clock.at = START.plus(Duration.ofDays(1));
        QuotaPolicySnapshot next = snapshot("FREE", 2, 20, 2);
        provider.users.put("u", entitlement("u", 0, next, 1, effective, null));
        recording.recordUsage(command(SPEAKING, 4, "today"));
        Document before = new Document(counter(SPEAKING));
        activations.onPolicyActivated(new QuotaPolicyActivated("late", "FREE", 2, 1, effective, Set.of(SPEAKING), next));
        assertThat(counter(SPEAKING)).isEqualTo(before);
    }

    @Test
    void invalidCanonicalUnitOrPeriodAndCorruptCounterFailWholeSummary() {
        availability.checkAvailability("u", SPEAKING);
        QuotaPolicySnapshot invalidUnit = new QuotaPolicySnapshot("FREE2", "FREE", 2,
                List.of(new QuotaRule(SPEAKING, "Speaking", 5, "MINUTE", "DAILY", 0)));
        assertThatThrownBy(() -> publishEntitlement(entitlement("u", 0, invalidUnit, 1, START, null))).isInstanceOf(QuotaUnavailableException.class);
        QuotaPolicySnapshot invalidPeriod = new QuotaPolicySnapshot("FREE2", "FREE", 2,
                List.of(new QuotaRule(SPEAKING, "Speaking", 5, "SECOND", "MONTHLY", 0)));
        assertThatThrownBy(() -> publishEntitlement(entitlement("u", 0, invalidPeriod, 1, START, null))).isInstanceOf(QuotaUnavailableException.class);
        recording.recordUsage(command(SPEAKING, 1, "one"));
        mongoTemplate.updateFirst(Query.query(Criteria.where("quotaType").is(SPEAKING)),
                new org.springframework.data.mongodb.core.query.Update().set("usedAmount", -1L), COUNTERS);
        assertThatThrownBy(() -> service.usage("u")).isInstanceOf(QuotaUnavailableException.class);
        assertThat(count(LEDGER)).isEqualTo(1);
    }

    @Test
    void zeroLimitIsExhaustedAndDeniedKeyCanBeReevaluatedAfterTierChange() {
        provider.users.put("u", entitlement("u", 0, snapshot("FREE", 1, 0, 0), 0, START.minusSeconds(1), null));
        assertThat(availability.checkAvailability("u", SPEAKING).status()).isEqualTo("EXHAUSTED");
        UsageCommand command = command(SPEAKING, 1, "denied-retry");
        assertThat(consumption.consume(command).accepted()).isFalse();
        assertThat(count(LEDGER)).isZero();
        publishEntitlement(entitlement("u", 1, snapshot("TRIAL", 1, 20, 4), 0, START, START.plusSeconds(60)));
        assertThat(consumption.consume(command).accepted()).isTrue();
    }

    @Test
    void deliveredActivationRacingAcceptedUsageRetriesWithoutLosingPostEffectiveCharge() throws Exception {
        recording.recordUsage(command(SPEAKING, 8, "before"));
        Instant effective = START.plusSeconds(3600);
        clock.at = effective.plusSeconds(10);
        QuotaPolicySnapshot next = snapshot("FREE", 2, 20, 2);
        provider.users.put("u", entitlement("u", 0, next, 1, effective, null));
        CountDownLatch handlerRead = new CountDownLatch(1);
        CountDownLatch usageCommitted = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean firstLookup = new java.util.concurrent.atomic.AtomicBoolean(true);
        provider.onLookup = user -> {
            if (Thread.currentThread().getName().equals("activation-race") && firstLookup.compareAndSet(true, false)) {
                handlerRead.countDown();
                try {
                    if (!usageCommitted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Usage timeout");
                } catch (InterruptedException exception) { throw new IllegalStateException(exception); }
            }
        };
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "activation-race"));
        try {
            Future<?> handler = worker.submit(() -> activations.onPolicyActivated(
                    new QuotaPolicyActivated("raced", "FREE", 2, 1, effective, Set.of(SPEAKING), next)));
            assertThat(handlerRead.await(10, TimeUnit.SECONDS)).isTrue();
            recording.recordUsage(command(SPEAKING, 4, "raced-usage"));
            usageCommitted.countDown();
            handler.get(20, TimeUnit.SECONDS);
            assertThat(availability.checkAvailability("u", SPEAKING).used()).isEqualTo(4);
            assertThat(count(LEDGER)).isEqualTo(2);
            assertThat(count(RECEIPTS)).isEqualTo(2);
        } finally { usageCommitted.countDown(); worker.shutdownNow(); }
    }

    @Test
    void failedActivationRollsBackEveryResetAndAllReceiptsAndScopeOrder() {
        recording.recordUsage(command(SPEAKING, 8, "first-user"));
        provider.users.put("other", entitlement("other", 0, snapshot("FREE", 1, 10, 2), 0, START.minusSeconds(1), null));
        recording.recordUsage(new UsageCommand("other", SPEAKING, 7, "TEST", "other-key", null, null));
        Instant effective = START.plusSeconds(3600);
        clock.at = effective.plusSeconds(10);
        QuotaPolicySnapshot next = snapshot("FREE", 2, 20, 2);
        provider.users.put("u", entitlement("u", 0, next, 1, effective, null));
        provider.users.put("other", entitlement("other", 0, next, 1, effective, null));
        List<Document> before = store.all(COUNTERS);
        provider.onLookup = user -> { if (user.equals("other")) throw new SubscriptionUnavailableException(); };
        assertThatThrownBy(() -> activations.onPolicyActivated(
                new QuotaPolicyActivated("failed", "FREE", 2, 1, effective, Set.of(SPEAKING), next)))
                .isInstanceOf(SubscriptionUnavailableException.class);
        assertThat(store.all(COUNTERS)).containsExactlyElementsOf(before);
        assertThat(count(RECEIPTS)).isZero();
        assertThat(count(LEDGER)).isEqualTo(2);
    }

    @Test
    void weeklyAndMonthlyRolloverReadZeroWithoutStorageWritesAndLazyWriteRetainsLedger() {
        consumption.consume(command(WEEKLY, 2, "week"));
        consumption.consume(command(ATTEMPT, 1, "month"));
        List<Document> before = store.all(COUNTERS);
        clock.at = Instant.parse("2026-11-01T17:00:00Z");
        assertThat(availability.checkAvailability("u", WEEKLY).used()).isZero();
        assertThat(availability.checkAvailability("u", ATTEMPT).used()).isZero();
        assertThat(store.all(COUNTERS)).containsExactlyElementsOf(before);
        consumption.consume(command(WEEKLY, 1, "next-week"));
        consumption.consume(command(ATTEMPT, 1, "next-month"));
        assertThat(availability.checkAvailability("u", WEEKLY).used()).isEqualTo(1);
        assertThat(availability.checkAvailability("u", ATTEMPT).used()).isEqualTo(1);
        assertThat(count(LEDGER)).isEqualTo(4);
    }
}
