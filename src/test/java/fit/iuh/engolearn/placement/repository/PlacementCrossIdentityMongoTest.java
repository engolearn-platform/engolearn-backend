package fit.iuh.engolearn.placement.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import fit.iuh.engolearn.placement.dto.response.PlacementClaimGateResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementSubmitResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.infra.PlacementMongoIndexConfig;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.service.PlacementAccessContext;
import fit.iuh.engolearn.placement.service.PlacementStartOutcome;
import fit.iuh.engolearn.placement.service.impl.PlacementAttemptServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementClaimServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementDeadlineServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlacementCrossIdentityMongoTest {
    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static MongoClient client;
    private static ScheduledMongo mongo;
    private final AtomicReference<Instant> time = new AtomicReference<>(NOW);
    private final PlacementAccessContext guest = new PlacementAccessContext(null, "same-browser");
    private final PlacementAccessContext user = new PlacementAccessContext("user-one", "same-browser");
    private final PlacementAccessContext independentUser = new PlacementAccessContext("user-one", null);
    private MongoPlacementAttemptRepository attempts;
    private PlacementAttemptServiceImpl service;
    private PlacementClaimServiceImpl claims;

    @BeforeAll
    static void connect() {
        client = MongoClients.create("mongodb://localhost:27017/?serverSelectionTimeoutMS=2000");
        client.getDatabase("admin").runCommand(new Document("ping", 1));
        String db = "placement_cross_identity_it_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(client.listDatabaseNames().into(new ArrayList<>())).doesNotContain(db);
        mongo = new ScheduledMongo(client, db);
        System.out.println("Cross-identity isolated database: " + db);
    }

    @AfterAll
    static void close() {
        if (mongo != null) mongo.getDb().drop();
        if (client != null) client.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        mongo.beforeInsert = ignored -> { };
        mongo.getDb().drop();
        new PlacementMongoIndexConfig().placementMongoIndexes(mongo).afterPropertiesSet();
        attempts = new MongoPlacementAttemptRepository(mongo);
        var sets = new SamplePlacementTestSetRepository(new ClassPathResource("placement/sample-test-set.json"),
                new ObjectMapper());
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> time.get());
        var deadlines = new PlacementDeadlineServiceImpl(attempts, sets);
        service = new PlacementAttemptServiceImpl(attempts, sets, clock, deadlines);
        claims = new PlacementClaimServiceImpl(attempts, deadlines, clock);
        mongo.insert(PlacementAttempt.builder().id("known-expired-session")
                .guestSessionId(guest.guestSessionId()).status(PlacementAttemptStatus.EXPIRED)
                .startedAt(NOW.minusSeconds(1800)).deadlineAt(NOW.minusSeconds(900))
                .endedAt(NOW.minusSeconds(900)).answers(new ArrayList<>()).build());
    }

    @Test
    void sequentialLoginResumesGuestWithoutChangingOwnershipDeadlineOrClaimWindow() {
        var start = service.start(guest);
        time.set(NOW.plusSeconds(60));
        var resumed = service.start(user);
        assertThat(resumed.created()).isFalse();
        assertThat(resumed.response().attemptId()).isEqualTo(start.response().attemptId());
        assertThat(resumed.response().deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(stored(start).getUserId()).isNull();
        complete(start, user);
        Instant expires = stored(start).getClaimExpiresAt();
        assertThat(expires).isEqualTo(time.get().plus(Duration.ofHours(2)));
        assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        assertThat(stored(start).getUserId()).isEqualTo(user.currentUserId());
        assertThat(stored(start).getStartSessionKey()).isNull();
        assertThat(stored(start).getClaimExpiresAt()).isEqualTo(expires);
    }

    @RepeatedTest(10)
    void staleAuthenticatedInsertLosesToCommittedGuestAndClaimHasNoPhantomUser() throws Exception {
        var pause = pauseInsert(a -> a.getUserId() != null);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> service.start(user));
            try {
                assertThat(pause.reached.await(10, TimeUnit.SECONDS)).isTrue();
                var start = service.start(guest);
                assertThat(stored(start).getStartSessionKey()).isEqualTo(guest.guestSessionId());
                pause.release.countDown();
                var resumed = pending.get(10, TimeUnit.SECONDS);
                assertThat(resumed.created()).isFalse();
                assertThat(resumed.response().attemptId()).isEqualTo(start.response().attemptId());
                assertOneGuestAndNoUser(start);
                assertThat(service.start(user).response().attemptId()).isEqualTo(start.response().attemptId());
                assertThat(service.start(guest).response().attemptId()).isEqualTo(start.response().attemptId());
                complete(start, user);
                assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                        .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
                assertThat(stored(start).getRevision()).isEqualTo(3);
                assertThat(stored(start).getUserId()).isEqualTo(user.currentUserId());
                assertThat(service.start(user).response().attemptId()).isEqualTo(start.response().attemptId());
                assertThat(userDocuments()).hasSize(1);
            } finally {
                pause.release.countDown();
            }
        }
    }

    @RepeatedTest(10)
    void guestSubmitBeforeStaleUserInsertRetainsReservationUntilClaim() throws Exception {
        var pause = pauseInsert(a -> a.getUserId() != null);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> service.start(user));
            try {
                assertThat(pause.reached.await(10, TimeUnit.SECONDS)).isTrue();
                var start = service.start(guest);
                complete(start, guest);
                assertThat(stored(start).getStartSessionKey()).isEqualTo(guest.guestSessionId());
                pause.release.countDown();
                var resumed = pending.get(10, TimeUnit.SECONDS);
                assertThat(resumed.created()).isFalse();
                assertThat(resumed.response().attemptId()).isEqualTo(start.response().attemptId());
                assertThat(resumed.response().status()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
                assertOneGuestAndNoUser(start);
                assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                        .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
                assertThat(stored(start).getStartSessionKey()).isNull();
            } finally {
                pause.release.countDown();
            }
        }
    }

    @RepeatedTest(10)
    void guestClaimBeforeStaleUserInsertIsProtectedByUserUniqueIndex() throws Exception {
        var pause = pauseInsert(a -> a.getUserId() != null);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> service.start(user));
            try {
                assertThat(pause.reached.await(10, TimeUnit.SECONDS)).isTrue();
                var start = service.start(guest);
                complete(start, guest);
                assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                        .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
                pause.release.countDown();
                var resumed = pending.get(10, TimeUnit.SECONDS);
                assertThat(resumed.created()).isFalse();
                assertThat(resumed.response().attemptId()).isEqualTo(start.response().attemptId());
                assertThat(resumed.response().status()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
                assertThat(stored(start).getUserId()).isEqualTo(user.currentUserId());
                assertThat(stored(start).getRevision()).isEqualTo(3);
                assertThat(userDocuments()).hasSize(1);
                assertThat(mongo.getCollection("placement_attempts").countDocuments()).isEqualTo(2);
            } finally {
                pause.release.countDown();
            }
        }
    }

    @RepeatedTest(10)
    void userWinningInsertRejectsGuestWithoutExposingUserAttemptOrCreatingAnother() throws Exception {
        var pause = pauseInsert(a -> a.getUserId() == null);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> capture(() -> service.start(guest)));
            try {
                assertThat(pause.reached.await(10, TimeUnit.SECONDS)).isTrue();
                var start = service.start(user);
                pause.release.countDown();
                assertConflict(pending.get(10, TimeUnit.SECONDS));
                assertThat(stored(start).getUserId()).isEqualTo(user.currentUserId());
                assertThat(stored(start).getGuestSessionId()).isNull();
                assertThat(stored(start).getStartSessionKey()).isEqualTo(guest.guestSessionId());
                assertThat(attempts.findActiveForGuest(guest.guestSessionId())).isEmpty();
                assertThat(userDocuments()).hasSize(1);
                assertThat(service.start(user).response().attemptId()).isEqualTo(start.response().attemptId());
                assertConflict(capture(() -> service.start(guest)));
                assertConflict(capture(() -> service.start(new PlacementAccessContext("other-user", guest.guestSessionId()))));
                assertThatThrownBy(() -> service.get(start.response().attemptId(), guest))
                        .isInstanceOfSatisfying(PlacementRequestException.class, e -> assertThat(e.status().value()).isEqualTo(404));
                assertThat(mongo.getCollection("placement_attempts").countDocuments()).isEqualTo(2);
            } finally {
                pause.release.countDown();
            }
        }
    }

    @Test
    void independentUserActivePreservesPolicyAAndGuestFirstThenUserPrecedence() throws Exception {
        var active = service.start(independentUser);
        var start = service.start(guest);
        assertThat(service.start(user).response().attemptId()).isEqualTo(start.response().attemptId());
        complete(start, guest);
        long revision = stored(start).getRevision();
        Instant expires = stored(start).getClaimExpiresAt();
        assertThat(service.start(user).response().attemptId()).isEqualTo(active.response().attemptId());
        assertConflict(capture(() -> claims.getClaimGate(start.response().attemptId(), user)));
        assertThat(stored(start).getUserId()).isNull();
        assertThat(stored(start).getRevision()).isEqualTo(revision);
        assertThat(stored(start).getClaimExpiresAt()).isEqualTo(expires);
        assertThat(stored(active).getStatus()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
    }

    @Test
    void distinctGuestSessionsCanStartAndCompleteIndependently() throws Exception {
        var other = new PlacementAccessContext(null, "other-browser");
        var starts = simultaneously(List.of(() -> service.start(guest), () -> service.start(other)));
        assertThat(starts).allMatch(PlacementStartOutcome.class::isInstance);
        var first = (PlacementStartOutcome) starts.get(0);
        var second = (PlacementStartOutcome) starts.get(1);
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isTrue();
        assertThat(stored(first).getStatus()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThat(stored(second).getStatus()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThat(stored(first).getStartSessionKey()).isNotEqualTo(stored(second).getStartSessionKey());
        complete(first, guest);
        complete(second, other);
        assertThat(stored(first).getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(stored(second).getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
    }

    @RepeatedTest(10)
    void concurrentStartClaimAndSubmitPreserveSingleUserResultAndRevisions() throws Exception {
        var active = service.start(independentUser);
        service.saveAnswer(active.response().attemptId(), "q01", "q01-a", independentUser);
        var start = service.start(guest);
        complete(start, guest);
        var responses = simultaneously(List.of(() -> service.start(independentUser),
                () -> claims.getClaimGate(start.response().attemptId(), user),
                () -> service.submit(active.response().attemptId(), independentUser)));
        assertThat(responses.get(0)).isInstanceOfSatisfying(PlacementStartOutcome.class,
                response -> assertThat(response.response().attemptId()).isEqualTo(active.response().attemptId()));
        if (responses.get(1) instanceof PlacementRequestException) assertConflict(responses.get(1));
        else assertThat(responses.get(1)).isInstanceOfSatisfying(
                PlacementClaimGateResponse.class,
                response -> assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.USER_HAS_RESULT));
        assertThat(responses.get(2)).isInstanceOfSatisfying(
                PlacementSubmitResponse.class,
                response -> assertThat(response.attemptId()).isEqualTo(active.response().attemptId()));
        assertThat(stored(active).getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(stored(active).getRevision()).isEqualTo(2);
        assertThat(stored(start).getUserId()).isNull();
        assertThat(stored(start).getRevision()).isEqualTo(2);
        assertThat(userDocuments()).hasSize(1);
        assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                .isEqualTo(PlacementClaimStatus.USER_HAS_RESULT);
        assertThat(attempts.saveAnswer(active.response().attemptId(), 1, "q01", "q01-b", NOW)).isEmpty();
    }

    @RepeatedTest(10)
    void independentUserStartRacingClaimCannotCreateTwoBlockingUserDocuments() throws Exception {
        var start = service.start(guest);
        complete(start, guest);
        var responses = simultaneously(List.of(() -> service.start(independentUser),
                () -> claims.getClaimGate(start.response().attemptId(), user)));
        assertThat(responses.get(0)).isInstanceOf(PlacementStartOutcome.class);
        if (responses.get(1) instanceof PlacementRequestException) assertConflict(responses.get(1));
        else assertThat(responses.get(1)).isInstanceOfSatisfying(
                PlacementClaimGateResponse.class,
                response -> assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED));
        assertThat(userDocuments()).hasSize(1);
        var winner = userDocuments().get(0);
        if (winner.getId().equals(start.response().attemptId())) {
            assertThat(winner.getRevision()).isEqualTo(3);
            assertThat(winner.getStartSessionKey()).isNull();
            assertThatThrownBy(() -> claims.getClaimGate(start.response().attemptId(),
                    new PlacementAccessContext("other-user", guest.guestSessionId())))
                    .isInstanceOfSatisfying(PlacementRequestException.class, e -> assertThat(e.status().value()).isEqualTo(404));
        } else {
            assertThat(winner.getStatus()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
            assertThat(stored(start).getUserId()).isNull();
            assertThat(stored(start).getRevision()).isEqualTo(2);
        }
    }

    @Test
    void guestDeadlineAutoSubmitRetainsReservationAndUsesOriginalDeadline() {
        var start = service.start(guest);
        service.saveAnswer(start.response().attemptId(), "q01", "q01-a", guest);
        Instant deadline = start.response().deadlineAt();
        time.set(deadline.plusSeconds(60));
        var resumed = service.start(user);
        assertThat(resumed.created()).isFalse();
        assertThat(resumed.response().attemptId()).isEqualTo(start.response().attemptId());
        assertThat(stored(start).getStatus()).isEqualTo(PlacementAttemptStatus.AUTO_SUBMITTED);
        assertThat(stored(start).getDeadlineAt()).isEqualTo(deadline);
        assertThat(stored(start).getEndedAt()).isEqualTo(deadline);
        assertThat(stored(start).getClaimExpiresAt()).isEqualTo(deadline.plus(Duration.ofHours(2)));
        assertThat(stored(start).getStartSessionKey()).isEqualTo(guest.guestSessionId());
        assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
    }

    @Test
    void unansweredGuestAndUserDeadlinesReleaseReservationWithoutChangingOldDeadline() {
        var start = service.start(guest);
        time.set(start.response().deadlineAt());
        var next = service.start(user);
        assertThat(next.created()).isTrue();
        assertExpired(start);
        assertThat(next.response().deadlineAt()).isEqualTo(time.get().plus(Duration.ofMinutes(15)));
        time.set(next.response().deadlineAt());
        var finalGuest = service.start(guest);
        assertThat(finalGuest.created()).isTrue();
        assertExpired(next);
        assertThat(stored(finalGuest).getStartSessionKey()).isEqualTo(guest.guestSessionId());
    }

    @Test
    void answeredUserDeadlineReleasesSessionWithoutLeakingUserResultToGuest() {
        var start = service.start(user);
        service.saveAnswer(start.response().attemptId(), "q01", "q01-a", user);
        time.set(start.response().deadlineAt().plusSeconds(60));
        var next = service.start(guest);
        assertThat(next.created()).isTrue();
        assertThat(next.response().attemptId()).isNotEqualTo(start.response().attemptId());
        assertThat(stored(start).getStatus()).isEqualTo(PlacementAttemptStatus.AUTO_SUBMITTED);
        assertThat(stored(start).getEndedAt()).isEqualTo(start.response().deadlineAt());
        assertThat(stored(start).getDeadlineAt()).isEqualTo(start.response().deadlineAt());
        assertThat(stored(start).getStartSessionKey()).isNull();
        assertThat(stored(start).getClaimExpiresAt()).isNull();
        assertThat(stored(start).getCorrectCount()).isEqualTo(1);
        assertThat(stored(next).getUserId()).isNull();
        assertThat(stored(next).getGuestSessionId()).isEqualTo(guest.guestSessionId());
    }

    @Test
    void submittedGuestDoesNotPermanentlyBlockSessionAfterClaimWindow() {
        var start = service.start(guest);
        complete(start, guest);
        Instant expires = stored(start).getClaimExpiresAt();
        assertThat(service.start(user).response().attemptId()).isEqualTo(start.response().attemptId());
        time.set(expires);
        var next = service.start(guest);
        assertThat(next.created()).isTrue();
        assertThat(stored(start).getStartSessionKey()).isNull();
        assertThat(stored(start).getClaimExpiresAt()).isEqualTo(expires);
        assertThat(stored(start).getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(stored(start).getCorrectCount()).isEqualTo(1);
        assertThat(stored(start).getRevision()).isEqualTo(3);
        assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                .isEqualTo(PlacementClaimStatus.CLAIM_EXPIRED);
    }

    @Test
    void userSubmitReleasesSessionButRetainsUserResultConstraint() {
        var start = service.start(user);
        complete(start, user);
        assertThat(stored(start).getStartSessionKey()).isNull();
        assertThat(stored(start).getGuestSessionId()).isNull();
        assertThat(stored(start).getClaimExpiresAt()).isNull();
        assertThat(service.start(guest).created()).isTrue();
        assertThat(service.start(independentUser).response().attemptId()).isEqualTo(start.response().attemptId());
        assertThat(userDocuments()).hasSize(1);
    }

    @Test
    void claimedGuestHistoryAllowsAnotherGuestStartButNotAnotherUserResult() {
        var start = service.start(guest);
        complete(start, guest);
        assertThat(claims.getClaimGate(start.response().attemptId(), user).claimStatus())
                .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        var next = service.start(guest);
        assertThat(next.created()).isTrue();
        assertThat(next.response().attemptId()).isNotEqualTo(start.response().attemptId());
        assertThat(stored(next).getUserId()).isNull();
        assertThat(stored(next).getStartSessionKey()).isEqualTo(guest.guestSessionId());
        assertThat(stored(start).getUserId()).isEqualTo(user.currentUserId());
        assertThat(stored(start).getStartSessionKey()).isNull();
        assertThat(service.start(independentUser).response().attemptId()).isEqualTo(start.response().attemptId());
        assertThat(userDocuments()).hasSize(1);
    }

    @Test
    void actualUniqueSessionIndexRejectsCrossIdentityInsertEvenWithoutPrechecks() {
        var start = service.start(guest);
        var index = mongo.indexOps(PlacementAttempt.class).getIndexInfo().stream()
                .filter(i -> "one_reserved_start_session".equals(i.getName())).findFirst().orElseThrow();
        assertThat(index.isUnique()).isTrue();
        assertThat(Document.parse(index.getPartialFilterExpression()))
                .isEqualTo(new Document("start_session_key", new Document("$type", "string")));
        assertThatThrownBy(() -> mongo.insert(PlacementAttempt.builder().id(UUID.randomUUID().toString())
                .userId(user.currentUserId()).startSessionKey(guest.guestSessionId())
                .status(PlacementAttemptStatus.IN_PROGRESS).build()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertOneGuestAndNoUser(start);
        assertThat(mongo.getDb().listCollectionNames().into(new ArrayList<>()))
                .contains("placement_attempts")
                .isSubsetOf("placement_questions", "placement_test_sets", "placement_attempts");
    }

    @Test
    void legacyBlockingGuestRequiresExplicitMigrationWithoutAutomaticBackfill() {
        mongo.insert(PlacementAttempt.builder().id("legacy-active").guestSessionId("legacy-browser")
                .status(PlacementAttemptStatus.IN_PROGRESS).startedAt(NOW)
                .deadlineAt(NOW.plus(Duration.ofMinutes(15))).answers(new ArrayList<>()).build());
        assertThatThrownBy(() -> new PlacementMongoIndexConfig().placementMongoIndexes(mongo).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit migration");
        assertThat(attempts.findById("legacy-active").orElseThrow().getStartSessionKey()).isNull();
        assertThat(attempts.findById("legacy-active").orElseThrow().getRevision()).isZero();
    }

    private PlacementAttempt stored(PlacementStartOutcome start) {
        return attempts.findById(start.response().attemptId()).orElseThrow();
    }

    private List<PlacementAttempt> userDocuments() {
        return mongo.find(Query.query(Criteria.where("user_id").is(user.currentUserId())
                .and("status").in(PlacementAttemptStatus.IN_PROGRESS, PlacementAttemptStatus.SUBMITTED,
                        PlacementAttemptStatus.AUTO_SUBMITTED)), PlacementAttempt.class);
    }

    private void complete(PlacementStartOutcome start, PlacementAccessContext access) {
        service.saveAnswer(start.response().attemptId(), "q01", "q01-a", access);
        service.submit(start.response().attemptId(), access);
    }

    private void assertExpired(PlacementStartOutcome start) {
        var attempt = stored(start);
        assertThat(attempt.getStatus()).isEqualTo(PlacementAttemptStatus.EXPIRED);
        assertThat(attempt.getStartSessionKey()).isNull();
        assertThat(attempt.getCorrectCount()).isNull();
        assertThat(attempt.getPlacementLevel()).isNull();
        assertThat(attempt.getEndedAt()).isEqualTo(start.response().deadlineAt());
        assertThat(attempt.getDeadlineAt()).isEqualTo(start.response().deadlineAt());
    }

    private void assertOneGuestAndNoUser(PlacementStartOutcome start) {
        assertThat(mongo.getCollection("placement_attempts").countDocuments()).isEqualTo(2);
        assertThat(stored(start).getUserId()).isNull();
        assertThat(userDocuments()).isEmpty();
        assertThat(mongo.count(Query.query(Criteria.where("start_session_key").is(guest.guestSessionId())),
                PlacementAttempt.class)).isEqualTo(1);
    }

    private void assertConflict(Object result) {
        assertThat(result).isInstanceOfSatisfying(PlacementRequestException.class, e -> {
            assertThat(e.status().value()).isEqualTo(409);
            assertThat(e.code()).isEqualTo("PLACEMENT_ACTIVE_ATTEMPT_EXISTS");
        });
    }

    private Object capture(Callable<?> operation) throws Exception {
        try {
            return operation.call();
        } catch (PlacementRequestException exception) {
            return exception;
        }
    }

    private List<Object> simultaneously(List<Callable<?>> operations) throws Exception {
        var ready = new CountDownLatch(operations.size());
        var fire = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(operations.size())) {
            var futures = operations.stream().map(operation -> pool.submit(() -> {
                ready.countDown();
                if (!fire.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race timeout");
                return capture(operation);
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            var results = new ArrayList<Object>();
            for (var future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            return results;
        }
    }

    private Pause pauseInsert(Predicate<PlacementAttempt> predicate) {
        var pause = new Pause(new CountDownLatch(1), new CountDownLatch(1));
        var once = new AtomicBoolean();
        mongo.beforeInsert = attempt -> {
            if (predicate.test(attempt) && once.compareAndSet(false, true)) {
                pause.reached.countDown();
                try {
                    if (!pause.release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("insert timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        };
        return pause;
    }

    private record Pause(CountDownLatch reached, CountDownLatch release) { }

    private static class ScheduledMongo extends MongoTemplate {
        private volatile Consumer<PlacementAttempt> beforeInsert = ignored -> { };

        ScheduledMongo(MongoClient client, String database) {
            super(new SimpleMongoClientDatabaseFactory(client, database));
        }

        @Override
        public <T> T insert(T object) {
            if (object instanceof PlacementAttempt attempt) beforeInsert.accept(attempt);
            return super.insert(object);
        }
    }
}
