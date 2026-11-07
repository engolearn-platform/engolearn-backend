package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementResultResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementReviewResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.repository.InMemoryPlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementTestSetRepository;
import fit.iuh.engolearn.placement.repository.SamplePlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAttemptServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementClaimServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementDeadlineServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementResultServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

class PlacementResultServiceTest {
    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final PlacementAccessContext user = new PlacementAccessContext("user-one", null);
    private final PlacementAccessContext guest = new PlacementAccessContext(null, "guest-one");
    private final PlacementAccessContext claimedUser = new PlacementAccessContext("user-one", "guest-one");
    private InMemoryPlacementAttemptRepository attempts;
    private SamplePlacementTestSetRepository sets;
    private PlacementAttemptService attemptService;
    private PlacementClaimService claimService;
    private PlacementResultService resultService;
    private PlacementDeadlineService deadlines;
    private Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> time.get());
        attempts = new InMemoryPlacementAttemptRepository();
        sets = new SamplePlacementTestSetRepository(new ClassPathResource("placement/sample-test-set.json"),
                new ObjectMapper());
        deadlines = new PlacementDeadlineServiceImpl(attempts, sets);
        attemptService = new PlacementAttemptServiceImpl(attempts, sets, clock, deadlines);
        claimService = new PlacementClaimServiceImpl(attempts, deadlines, clock);
        resultService = new PlacementResultServiceImpl(attempts, sets, deadlines, clock);
    }

    @Test
    void submittedOwnerResultUsesStoredScoreLevelAndBusinessEndTime() {
        String id = completed(user);
        PlacementAttempt before = attempts.findById(id).orElseThrow();
        PlacementResultResponse result = resultService.getResult(id, user.currentUserId());
        assertThat(result.attemptId()).isEqualTo(id);
        assertThat(result.status()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(result.score()).isEqualTo(before.getCorrectCount()).isEqualTo(2);
        assertThat(result.placementLevel()).isEqualTo(before.getPlacementLevel()).isEqualTo(PlacementLevel.A1);
        assertThat(result.completedAt()).isEqualTo(before.getEndedAt());
        assertThat(result.totalQuestions()).isEqualTo(20);
        assertThat(result.summary().incorrectCount()).isEqualTo(1);
        assertThat(result.summary().unansweredCount()).isEqualTo(17);
        assertThat(result.score() + result.summary().incorrectCount() + result.summary().unansweredCount())
                .isEqualTo(result.totalQuestions());
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(before.getRevision());
    }

    @Test
    void reviewPreservesSnapshotOrderAnswersAndPinnedScoresWithoutMutatingAttempt() {
        String id = completed(user);
        long revision = attempts.findById(id).orElseThrow().getRevision();
        PlacementReviewResponse review = resultService.getReview(id, user.currentUserId());
        PlacementResultResponse result = resultService.getResult(id, user.currentUserId());
        assertThat(review.questions()).hasSize(20);
        assertThat(review.questions()).extracting(q -> q.order()).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(review.questions().subList(0, 14)).allMatch(q -> q.section() == PlacementSkill.READING
                && q.audio() == null);
        assertThat(review.questions().subList(14, 20)).allMatch(q -> q.section() == PlacementSkill.LISTENING
                && q.audio() != null && q.audio().url() == null);
        assertThat(review.questions().get(0).selectedOptionId()).isEqualTo("q01-a");
        assertThat(review.questions().get(0).correctOptionId()).isEqualTo("q01-a");
        assertThat(review.questions().get(2).selectedOptionId()).isEqualTo("q03-b");
        assertThat(review.questions().get(2).correctOptionId()).isEqualTo("q03-a");
        assertThat(review.questions().get(3).selectedOptionId()).isNull();
        assertThat(review.questions().get(3).correctOptionId()).isEqualTo("q04-a");
        assertThat(review.score()).isEqualTo(result.score());
        assertThat(review.placementLevel()).isEqualTo(result.placementLevel());
        assertThat(review.incorrectCount()).isEqualTo(result.summary().incorrectCount());
        assertThat(review.unansweredCount()).isEqualTo(result.summary().unansweredCount());
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(revision);
    }

    @Test
    void deadlineResolvedOnceAndAutoSubmittedOwnerCanReadResultAndReview() {
        String id = attemptService.start(user).response().attemptId();
        attemptService.saveAnswer(id, "q01", "q01-a", user);
        Instant deadline = attempts.findById(id).orElseThrow().getDeadlineAt();
        time.set(deadline.plus(Duration.ofMinutes(10)));
        var result = resultService.getResult(id, user.currentUserId());
        assertThat(result.status()).isEqualTo(PlacementAttemptStatus.AUTO_SUBMITTED);
        assertThat(result.completedAt()).isEqualTo(deadline);
        assertThat(result.score()).isEqualTo(1);
        long revision = attempts.findById(id).orElseThrow().getRevision();
        assertThat(resultService.getReview(id, user.currentUserId()).status())
                .isEqualTo(PlacementAttemptStatus.AUTO_SUBMITTED);
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(revision);
    }

    @Test
    void guestMustClaimBeforeAuthenticatedUserMayReadEitherView() {
        String id = completed(guest);
        assertUnavailable(() -> resultService.getResult(id, null), HttpStatus.UNAUTHORIZED,
                "PLACEMENT_AUTHENTICATION_REQUIRED");
        assertUnavailable(() -> resultService.getReview(id, null), HttpStatus.UNAUTHORIZED,
                "PLACEMENT_AUTHENTICATION_REQUIRED");
        assertUnavailable(() -> resultService.getResult(id, user.currentUserId()), HttpStatus.NOT_FOUND,
                "PLACEMENT_ATTEMPT_NOT_FOUND");
        assertUnavailable(() -> resultService.getReview(id, user.currentUserId()), HttpStatus.NOT_FOUND,
                "PLACEMENT_ATTEMPT_NOT_FOUND");
        assertThat(claimService.getClaimGate(id, claimedUser).claimStatus())
                .isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        assertThat(resultService.getResult(id, user.currentUserId()).attemptId()).isEqualTo(id);
        assertThat(resultService.getReview(id, user.currentUserId()).attemptId()).isEqualTo(id);
        assertUnavailable(() -> resultService.getResult(id, "user-two"), HttpStatus.NOT_FOUND,
                "PLACEMENT_ATTEMPT_NOT_FOUND");
        assertUnavailable(() -> resultService.getReview(id, "user-two"), HttpStatus.NOT_FOUND,
                "PLACEMENT_ATTEMPT_NOT_FOUND");
    }

    @Test
    void inProgressAndExpiredOwnersHaveNeitherResultNorReview() {
        String id = attemptService.start(user).response().attemptId();
        assertUnavailable(() -> resultService.getResult(id, user.currentUserId()), HttpStatus.NOT_FOUND,
                "PLACEMENT_RESULT_NOT_AVAILABLE");
        assertUnavailable(() -> resultService.getReview(id, user.currentUserId()), HttpStatus.NOT_FOUND,
                "PLACEMENT_REVIEW_NOT_AVAILABLE");
        time.set(time.get().plus(Duration.ofMinutes(15)));
        assertUnavailable(() -> resultService.getResult(id, user.currentUserId()), HttpStatus.NOT_FOUND,
                "PLACEMENT_RESULT_NOT_AVAILABLE");
        assertUnavailable(() -> resultService.getReview(id, user.currentUserId()), HttpStatus.NOT_FOUND,
                "PLACEMENT_REVIEW_NOT_AVAILABLE");
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo(PlacementAttemptStatus.EXPIRED);
        assertThat(attempts.findById(id).orElseThrow().getCorrectCount()).isNull();
        assertThat(attempts.findById(id).orElseThrow().getPlacementLevel()).isNull();
    }

    @Test
    void userHasResultRedirectIdWorksWhileGuestResultRemainsUnclaimed() {
        String existingId = completed(user);
        String guestId = completed(guest);
        var gate = claimService.getClaimGate(guestId, claimedUser);
        assertThat(gate.claimStatus()).isEqualTo(PlacementClaimStatus.USER_HAS_RESULT);
        assertThat(gate.resultAttemptId()).isEqualTo(existingId);
        assertThat(resultService.getResult(gate.resultAttemptId(), user.currentUserId()).attemptId())
                .isEqualTo(existingId);
        assertThat(resultService.getReview(gate.resultAttemptId(), user.currentUserId()).attemptId())
                .isEqualTo(existingId);
        assertUnavailable(() -> resultService.getResult(guestId, user.currentUserId()), HttpStatus.NOT_FOUND,
                "PLACEMENT_ATTEMPT_NOT_FOUND");
    }

    @Test
    void archivedPinnedVersionAndChangedDetachedSnapshotDoNotAlterPreviousReview() {
        String id = completed(user);
        PlacementTestSet pinned = sets.findVersion("placement-sample-v1", 1).orElseThrow();
        String original = pinned.getItems().get(0).getPrompt();
        pinned.getItems().get(0).setPrompt("Edited question bank prompt");
        pinned.getItems().get(0).setCorrectOptionId("q01-b");
        assertThat(resultService.getReview(id, user.currentUserId()).questions().get(0).prompt())
                .isEqualTo(original);

        PlacementTestSet archived = sets.findVersion("placement-sample-v1", 1).orElseThrow();
        archived.setStatus(PlacementTestSetStatus.ARCHIVED);
        PlacementTestSetRepository archivedSource = new PlacementTestSetRepository() {
            @Override public Optional<PlacementTestSet> findPublished() { return Optional.empty(); }
            @Override public Optional<PlacementTestSet> findVersion(String setId, int version) {
                return archived.getId().equals(setId) && archived.getVersion() == version
                        ? Optional.of(archived) : Optional.empty();
            }
        };
        var archivedResultService = new PlacementResultServiceImpl(attempts, archivedSource, deadlines, clock);
        assertThat(archivedResultService.getReview(id, user.currentUserId()).questions().get(0).prompt())
                .isEqualTo(original);
    }

    @Test
    void missingPinnedVersionFailsForBothViewsWithoutFallingBackToPublishedVersion() {
        String id = completed(user);
        PlacementTestSetRepository missing = new PlacementTestSetRepository() {
            @Override public Optional<PlacementTestSet> findPublished() { return sets.findPublished(); }
            @Override public Optional<PlacementTestSet> findVersion(String setId, int version) { return Optional.empty(); }
        };
        var service = new PlacementResultServiceImpl(attempts, missing, deadlines, clock);
        assertUnavailable(() -> service.getResult(id, user.currentUserId()), HttpStatus.CONFLICT,
                "PLACEMENT_TEST_SET_UNAVAILABLE");
        assertUnavailable(() -> service.getReview(id, user.currentUserId()), HttpStatus.CONFLICT,
                "PLACEMENT_TEST_SET_UNAVAILABLE");
    }

    @Test
    void inconsistentStoredCountsAreRejectedRatherThanReturningNegativeSummary() {
        String id = completed(user);
        PlacementAttempt corrupted = attempts.findById(id).orElseThrow();
        corrupted.setCorrectCount(4);
        var broken = org.mockito.Mockito.mock(InMemoryPlacementAttemptRepository.class);
        when(broken.findById(id)).thenReturn(Optional.of(corrupted));
        var service = new PlacementResultServiceImpl(broken, sets, deadlines, clock);
        assertUnavailable(() -> service.getResult(id, user.currentUserId()), HttpStatus.CONFLICT,
                "PLACEMENT_RESULT_INCONSISTENT");
        assertUnavailable(() -> service.getReview(id, user.currentUserId()), HttpStatus.CONFLICT,
                "PLACEMENT_RESULT_INCONSISTENT");
    }

    private String completed(PlacementAccessContext access) {
        String id = attemptService.start(access).response().attemptId();
        attemptService.saveAnswer(id, "q01", "q01-a", access);
        attemptService.saveAnswer(id, "q02", "q02-a", access);
        attemptService.saveAnswer(id, "q03", "q03-b", access);
        attemptService.submit(id, access);
        return id;
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
                                   HttpStatus status, String code) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(PlacementRequestException.class, e -> {
            assertThat(e.status()).isEqualTo(status);
            assertThat(e.code()).isEqualTo(code);
        });
    }
}
