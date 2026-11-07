package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementClaimGateResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.repository.InMemoryPlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.SamplePlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAttemptServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementClaimServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementDeadlineServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlacementClaimServiceTest {
    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final PlacementAccessContext guest = new PlacementAccessContext(null, "guest-one");
    private final PlacementAccessContext user = new PlacementAccessContext("user-one", "guest-one");
    private InMemoryPlacementAttemptRepository attempts;
    private PlacementAttemptService attemptService;
    private PlacementClaimService claimService;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> time.get());
        attempts = new InMemoryPlacementAttemptRepository();
        var sets = new SamplePlacementTestSetRepository(new ClassPathResource("placement/sample-test-set.json"),
                new ObjectMapper());
        var deadlines = new PlacementDeadlineServiceImpl(attempts, sets);
        attemptService = new PlacementAttemptServiceImpl(attempts, sets, clock, deadlines);
        claimService = new PlacementClaimServiceImpl(attempts, deadlines, clock);
    }

    @Test
    void guestResultIsClaimableJustBeforeTwoHoursAndExpiresAtExactBoundary() {
        String id = completedGuest();
        Instant endedAt = attempts.findById(id).orElseThrow().getEndedAt();
        time.set(endedAt.plus(Duration.ofHours(2)).minusNanos(1));
        PlacementClaimGateResponse claimable = claimService.getClaimGate(id, guest);
        assertThat(claimable.claimStatus()).isEqualTo(PlacementClaimStatus.CLAIMABLE);
        assertThat(claimable.claimExpiresAt()).isEqualTo(endedAt.plus(Duration.ofHours(2)));
        assertThat(claimable.resultAttemptId()).isNull();
        time.set(claimable.claimExpiresAt());
        PlacementClaimGateResponse expired = claimService.getClaimGate(id, user);
        assertThat(expired.claimStatus()).isEqualTo(PlacementClaimStatus.CLAIM_EXPIRED);
        assertThat(expired.resultAttemptId()).isNull();
        assertThat(attempts.findById(id).orElseThrow().getUserId()).isNull();
    }

    @Test
    void expiredUnansweredAttemptHasNoResultOrClaimWindow() {
        String id = attemptService.start(guest).response().attemptId();
        time.set(time.get().plus(Duration.ofMinutes(15)));
        PlacementClaimGateResponse response = claimService.getClaimGate(id, guest);
        assertThat(response.attemptStatus()).isEqualTo(PlacementAttemptStatus.EXPIRED);
        assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.NO_RESULT);
        assertThat(response.claimExpiresAt()).isNull();
        assertThat(response.resultAttemptId()).isNull();
        assertThat(attempts.findById(id).orElseThrow().getCorrectCount()).isNull();
        assertThat(attempts.findById(id).orElseThrow().getPlacementLevel()).isNull();
    }

    @Test
    void authenticatedGuestClaimIsLazyIdempotentAndRejectsGuestOnlyAccessAfterward() {
        String id = completedGuest();
        long revision = attempts.findById(id).orElseThrow().getRevision();
        PlacementClaimGateResponse claimed = claimService.getClaimGate(id, user);
        assertThat(claimed.claimStatus()).isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        assertThat(claimed.resultAttemptId()).isEqualTo(id);
        assertThat(attempts.findById(id).orElseThrow().getUserId()).isEqualTo(user.currentUserId());
        assertThat(attempts.findById(id).orElseThrow().getGuestSessionId()).isEqualTo(guest.guestSessionId());
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(revision + 1);
        assertThat(claimService.getClaimGate(id, new PlacementAccessContext(user.currentUserId(), null)).claimStatus())
                .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(revision + 1);
        assertThat(attemptService.get(id, new PlacementAccessContext(user.currentUserId(), null)).claimRequired()).isFalse();
        assertThat(attemptService.start(new PlacementAccessContext(user.currentUserId(), null)).response().claimRequired()).isFalse();
        assertNotFound(() -> claimService.getClaimGate(id, guest));
        assertNotFound(() -> attemptService.get(id, guest));
    }

    @Test
    void existingUserResultWinsWithoutClaimingGuestResultOrReturningGuestId() {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String existing = attemptService.start(userOnly).response().attemptId();
        attemptService.saveAnswer(existing, "q01", "q01-a", userOnly);
        attemptService.submit(existing, userOnly);
        String guestId = completedGuest();

        var start = attemptService.start(user);
        assertThat(start.created()).isFalse();
        assertThat(start.response().attemptId()).isEqualTo(existing);
        assertThat(start.response().claimRequired()).isFalse();

        var response = claimService.getClaimGate(guestId, user);
        assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.USER_HAS_RESULT);
        assertThat(response.resultAttemptId()).isEqualTo(existing).isNotEqualTo(guestId);
        assertThat(attempts.findById(guestId).orElseThrow().getUserId()).isNull();
    }

    @Test
    void guestClaimableResultPreventsNewAuthenticatedAttemptEvenWithoutExistingUserResult() {
        String guestId = completedGuest();
        var start = attemptService.start(user);
        assertThat(start.created()).isFalse();
        assertThat(start.response().attemptId()).isEqualTo(guestId);
        assertThat(start.response().claimRequired()).isTrue();
        assertThat(attempts.findValidResultForUser(user.currentUserId())).isEmpty();
        assertThat(attempts.findById(guestId).orElseThrow().getUserId()).isNull();
    }

    @Test
    void wrongOrMissingCookieAndOtherUserCannotEnumerateOrClaimAttempt() {
        String id = completedGuest();
        for (PlacementAccessContext access : List.of(
                new PlacementAccessContext(null, null),
                new PlacementAccessContext(null, "wrong"),
                new PlacementAccessContext("user-two", "wrong"))) {
            assertNotFound(() -> claimService.getClaimGate(id, access));
        }
        claimService.getClaimGate(id, user);
        assertNotFound(() -> claimService.getClaimGate(id, new PlacementAccessContext("user-two", "guest-one")));
        assertThat(attempts.findById(id).orElseThrow().getUserId()).isEqualTo(user.currentUserId());
    }

    @Test
    void loginMidTestDoesNotBindAttemptAndClaimGateBeforeDeadlineRejects() {
        String id = attemptService.start(guest).response().attemptId();
        assertThat(attemptService.get(id, user).status()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThatThrownBy(() -> claimService.getClaimGate(id, user))
                .isInstanceOfSatisfying(PlacementRequestException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(attempts.findById(id).orElseThrow().getUserId()).isNull();
        attemptService.saveAnswer(id, "q01", "q01-a", user);
        attemptService.submit(id, user);
        assertThat(attempts.findById(id).orElseThrow().getUserId()).isNull();
        assertThat(claimService.getClaimGate(id, user).claimStatus()).isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
    }

    @Test
    void staleDeadlineAutoSubmitsAndUsesDeadlineNotProcessingTimeForClaimWindow() {
        String id = attemptService.start(guest).response().attemptId();
        attemptService.saveAnswer(id, "q01", "q01-a", guest);
        Instant deadline = attempts.findById(id).orElseThrow().getDeadlineAt();
        time.set(deadline.plus(Duration.ofHours(2)).minusNanos(1));
        var response = claimService.getClaimGate(id, guest);
        assertThat(response.attemptStatus()).isEqualTo(PlacementAttemptStatus.AUTO_SUBMITTED);
        assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.CLAIMABLE);
        assertThat(response.claimExpiresAt()).isEqualTo(deadline.plus(Duration.ofHours(2)));
        time.set(response.claimExpiresAt());
        assertThat(claimService.getClaimGate(id, user).claimStatus()).isEqualTo(PlacementClaimStatus.CLAIM_EXPIRED);
        assertThat(attempts.findById(id).orElseThrow().getUserId()).isNull();
    }

    @Test
    void simultaneousClaimsForSameUserMutateOnce() throws Exception {
        String id = completedGuest();
        long revision = attempts.findById(id).orElseThrow().getRevision();
        var responses = race(() -> claimService.getClaimGate(id, user),
                () -> claimService.getClaimGate(id, user));
        assertThat(responses).allMatch(r -> r.claimStatus() == PlacementClaimStatus.ALREADY_CLAIMED
                && id.equals(r.resultAttemptId()));
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(revision + 1);
    }

    @Test
    void simultaneousClaimsFromDifferentUsersNeverTransferOwnership() throws Exception {
        String id = completedGuest();
        var other = new PlacementAccessContext("user-two", "guest-one");
        var responses = raceResult(() -> claimService.getClaimGate(id, user),
                () -> claimService.getClaimGate(id, other));
        assertThat(responses).filteredOn(PlacementClaimGateResponse.class::isInstance)
                .hasSize(1);
        assertThat(responses).filteredOn(PlacementRequestException.class::isInstance).hasSize(1);
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(3);
    }

    @Test
    void directUserResultIsAlreadyOwnedWithoutGuestSession() {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String id = attemptService.start(userOnly).response().attemptId();
        attemptService.saveAnswer(id, "q01", "q01-a", userOnly);
        attemptService.submit(id, userOnly);
        PlacementClaimGateResponse response = claimService.getClaimGate(id, userOnly);
        assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        assertThat(response.resultAttemptId()).isEqualTo(id);
        assertNotFound(() -> claimService.getClaimGate(id, new PlacementAccessContext("user-two", null)));
    }

    @Test
    void activeUserAttemptRejectsGuestClaimAndLeavesBothAttemptsUnchanged() {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String active = attemptService.start(userOnly).response().attemptId();
        attemptService.saveAnswer(active, "q01", "q01-a", userOnly);
        String guestId = completedGuest();
        Instant claimExpiresAt = attempts.findById(guestId).orElseThrow().getClaimExpiresAt();

        assertThatThrownBy(() -> claimService.getClaimGate(guestId, user))
                .isInstanceOfSatisfying(PlacementRequestException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.code()).isEqualTo("PLACEMENT_ACTIVE_ATTEMPT_EXISTS");
                });

        assertThat(attempts.findById(active).orElseThrow().getStatus()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        var guest = attempts.findById(guestId).orElseThrow();
        assertThat(guest.getUserId()).isNull();
        assertThat(guest.getStatus()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(guest.getClaimExpiresAt()).isEqualTo(claimExpiresAt);
        assertThat(attempts.findValidResultForUser(user.currentUserId())).isEmpty();
    }

    @Test
    void submittedUserAttemptBecomesUserResultAndWinsGuestClaim() {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String active = attemptService.start(userOnly).response().attemptId();
        attemptService.saveAnswer(active, "q01", "q01-a", userOnly);
        String guestId = completedGuest();

        assertThatThrownBy(() -> claimService.getClaimGate(guestId, user))
                .isInstanceOfSatisfying(PlacementRequestException.class,
                        e -> assertThat(e.code()).isEqualTo("PLACEMENT_ACTIVE_ATTEMPT_EXISTS"));

        attemptService.submit(active, userOnly);
        var response = claimService.getClaimGate(guestId, user);
        assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.USER_HAS_RESULT);
        assertThat(response.resultAttemptId()).isEqualTo(active);
        assertThat(attempts.findById(guestId).orElseThrow().getUserId()).isNull();
    }

    @Test
    void pastDeadlineUserAttemptWithAnswersAutoSubmitsAndWinsGuestClaim() {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String active = attemptService.start(userOnly).response().attemptId();
        attemptService.saveAnswer(active, "q01", "q01-a", userOnly);
        String guestId = completedGuest();

        time.set(time.get().plus(Duration.ofMinutes(15)));
        var response = claimService.getClaimGate(guestId, user);

        assertThat(attempts.findById(active).orElseThrow().getStatus())
                .isEqualTo(PlacementAttemptStatus.AUTO_SUBMITTED);
        assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.USER_HAS_RESULT);
        assertThat(response.resultAttemptId()).isEqualTo(active);
        assertThat(attempts.findById(guestId).orElseThrow().getUserId()).isNull();
    }

    @Test
    void pastDeadlineUnansweredUserAttemptExpiresAndDoesNotBlockGuestClaim() {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String active = attemptService.start(userOnly).response().attemptId();
        String guestId = completedGuest();

        time.set(time.get().plus(Duration.ofMinutes(15)));
        var response = claimService.getClaimGate(guestId, user);

        assertThat(attempts.findById(active).orElseThrow().getStatus()).isEqualTo(PlacementAttemptStatus.EXPIRED);
        assertThat(response.claimStatus()).isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        assertThat(response.resultAttemptId()).isEqualTo(guestId);
        assertThat(attempts.findById(guestId).orElseThrow().getUserId()).isEqualTo(user.currentUserId());
    }

    @Test
    void startResumesActiveUserAttemptInsteadOfClaimableGuestResult() {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String active = attemptService.start(userOnly).response().attemptId();
        attemptService.saveAnswer(active, "q01", "q01-a", userOnly);
        String guestId = completedGuest();

        var start = attemptService.start(user);

        assertThat(start.created()).isFalse();
        assertThat(start.response().attemptId()).isEqualTo(active);
        assertThat(start.response().status()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThat(start.response().deadlineAt()).isEqualTo(attempts.findById(active).orElseThrow().getDeadlineAt());
        assertThat(start.response().attemptId()).isNotEqualTo(guestId);
    }

    @Test
    void loginMidTestStillResumesGuestAttemptBeforeActiveUserAttempt() {
        String guestId = attemptService.start(guest).response().attemptId();
        attemptService.saveAnswer(guestId, "q01", "q01-a", guest);

        var start = attemptService.start(user);

        assertThat(start.response().attemptId()).isEqualTo(guestId);
        assertThat(start.response().status()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThat(attempts.findActiveForUser(user.currentUserId())).isEmpty();
    }

    @Test
    void claimRacingWithUserStartNeverLeavesClaimedGuestAndActiveUserAttemptTogether() throws Exception {
        String guestId = completedGuest();
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        raceResult(() -> claimService.getClaimGate(guestId, user), () -> attemptService.start(userOnly));

        var guest = attempts.findById(guestId).orElseThrow();
        var active = attempts.findActiveForUser(user.currentUserId());
        var result = attempts.findValidResultForUser(user.currentUserId());
        assertThat(active.isPresent() && guest.getUserId() != null).isFalse();
        assertThat(result.isPresent() && active.isPresent()).isFalse();
        if (result.isPresent()) {
            assertThat(result.get().getId()).isEqualTo(guestId);
            assertThat(guest.getUserId()).isEqualTo(user.currentUserId());
        }
        if (active.isPresent()) {
            assertThat(guest.getUserId()).isNull();
        }
    }

    @Test
    void claimRacingWithUserSubmitNeverProducesTwoResults() throws Exception {
        var userOnly = new PlacementAccessContext(user.currentUserId(), null);
        String active = attemptService.start(userOnly).response().attemptId();
        attemptService.saveAnswer(active, "q01", "q01-a", userOnly);
        String guestId = completedGuest();

        raceResult(() -> claimService.getClaimGate(guestId, user), () -> attemptService.submit(active, userOnly));

        assertThat(attempts.findActiveForUser(user.currentUserId())).isEmpty();
        assertThat(attempts.findValidResultForUser(user.currentUserId()).orElseThrow().getId()).isEqualTo(active);
        assertThat(attempts.findById(guestId).orElseThrow().getUserId()).isNull();
    }

    private String completedGuest() {
        String id = attemptService.start(guest).response().attemptId();
        attemptService.saveAnswer(id, "q01", "q01-a", guest);
        attemptService.submit(id, guest);
        return id;
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(PlacementRequestException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private List<PlacementClaimGateResponse> race(java.util.concurrent.Callable<PlacementClaimGateResponse> first,
                                                  java.util.concurrent.Callable<PlacementClaimGateResponse> second) throws Exception {
        return raceResult(first, second).stream().map(PlacementClaimGateResponse.class::cast).toList();
    }

    private List<Object> raceResult(java.util.concurrent.Callable<?> first, java.util.concurrent.Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(operation -> executor.submit(() -> {
                ready.countDown();
                fire.await();
                try {
                    return operation.call();
                } catch (PlacementRequestException e) {
                    return e;
                }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            return List.of(futures.get(0).get(), futures.get(1).get());
        }
    }
}
