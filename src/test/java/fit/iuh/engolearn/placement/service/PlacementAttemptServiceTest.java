package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementAttemptResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.*;
import fit.iuh.engolearn.placement.repository.InMemoryPlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementTestSetRepository;
import fit.iuh.engolearn.placement.repository.SamplePlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAttemptServiceImpl;
import fit.iuh.engolearn.placement.service.impl.PlacementDeadlineServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlacementAttemptServiceTest {
    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private InMemoryPlacementAttemptRepository attempts;
    private SamplePlacementTestSetRepository sets;
    private PlacementAttemptService service;
    private final PlacementAccessContext guest = new PlacementAccessContext(null, "guest-one");
    private final PlacementAccessContext user = new PlacementAccessContext("user-one", null);

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> time.get());
        attempts = new InMemoryPlacementAttemptRepository();
        sets = new SamplePlacementTestSetRepository(new ClassPathResource("placement/sample-test-set.json"),
                new ObjectMapper());
        service = new PlacementAttemptServiceImpl(attempts, sets, clock,
                new PlacementDeadlineServiceImpl(attempts, sets));
    }

    @Test
    void sampleIsPublishedAndMatchesExactBlueprint() {
        PlacementTestSet set = sets.findPublished().orElseThrow();
        assertThat(set.getItems()).hasSize(20);
        assertThat(set.getItems().subList(0, 14)).allMatch(i -> i.getSkill() == PlacementSkill.READING);
        assertThat(set.getItems().subList(14, 20)).allMatch(i -> i.getSkill() == PlacementSkill.LISTENING
                && i.getAudio() != null);
        Map<PlacementLevel, Map<PlacementSkill, Long>> counts = set.getItems().stream().collect(
                Collectors.groupingBy(i -> i.getLevel(), Collectors.groupingBy(i -> i.getSkill(), Collectors.counting())));
        assertThat(counts.get(PlacementLevel.A1)).containsEntry(PlacementSkill.READING, 4L).containsEntry(PlacementSkill.LISTENING, 1L);
        assertThat(counts.get(PlacementLevel.A2)).containsEntry(PlacementSkill.READING, 4L).containsEntry(PlacementSkill.LISTENING, 1L);
        assertThat(counts.get(PlacementLevel.B1)).containsEntry(PlacementSkill.READING, 3L).containsEntry(PlacementSkill.LISTENING, 2L);
        assertThat(counts.get(PlacementLevel.B2)).containsEntry(PlacementSkill.READING, 3L).containsEntry(PlacementSkill.LISTENING, 2L);
        assertThat(set.getItems()).allMatch(i -> i.getOptions().size() == 4
                && i.getOptions().stream().filter(o -> o.getId().equals(i.getCorrectOptionId())).count() == 1);
    }

    @Test
    void startResumesGuestAndUserWithoutResettingDeadline() {
        var first = service.start(guest);
        var firstUser = service.start(user);
        time.set(time.get().plus(Duration.ofMinutes(4)));
        assertThat(first.created()).isTrue();
        assertThat(first.response().status()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThat(first.response().deadlineAt()).isEqualTo(Instant.parse("2026-01-01T00:15:00Z"));
        assertThat(service.start(guest).response()).isEqualTo(first.response());
        assertThat(service.start(user).response()).isEqualTo(firstUser.response());
        assertThat(firstUser.response().attemptId()).isNotEqualTo(first.response().attemptId());
        assertThat(service.get(first.response().attemptId(), guest).answers()).isEmpty();
    }

    @Test
    void saveReplaceAndClearAreIsolatedAndValidated() {
        String id = start(guest);
        service.saveAnswer(id, "q01", "q01-b", guest);
        service.saveAnswer(id, "q01", "q01-a", guest);
        assertThat(service.get(id, guest).answers()).singleElement().satisfies(a ->
                assertThat(a.selectedOptionId()).isEqualTo("q01-a"));
        service.saveAnswer(id, "q01", null, guest);
        assertThat(service.get(id, guest).answers()).isEmpty();
        assertThatThrownBy(() -> service.saveAnswer(id, "unknown", "q01-a", guest))
                .isInstanceOfSatisfying(PlacementRequestException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.saveAnswer(id, "q01", "q02-a", guest))
                .isInstanceOfSatisfying(PlacementRequestException.class, e -> assertThat(e.code()).isEqualTo("PLACEMENT_INVALID_OPTION"));
    }

    @Test
    void wrongGuestAndDifferentUserCannotReadOrWrite() {
        String guestId = start(guest);
        String userId = start(user);
        for (var access : List.of(new PlacementAccessContext(null, "wrong-guest"), user)) {
            assertThatThrownBy(() -> service.get(guestId, access)).isInstanceOfSatisfying(
                    PlacementRequestException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
            assertThatThrownBy(() -> service.saveAnswer(guestId, "q01", "q01-a", access))
                    .isInstanceOf(PlacementRequestException.class);
        }
        var otherUser = new PlacementAccessContext("user-two", null);
        assertThatThrownBy(() -> service.get(userId, otherUser)).isInstanceOf(PlacementRequestException.class);
        assertThatThrownBy(() -> service.submit(userId, otherUser)).isInstanceOf(PlacementRequestException.class);
        assertThat(service.get(guestId, new PlacementAccessContext("user-one", "guest-one")).status())
                .isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThat(service.start(new PlacementAccessContext("user-one", "guest-one")).response().attemptId())
                .isEqualTo(guestId);
    }

    @Test
    void deadlineExpiresUnansweredAndAllowsNewGuestAttempt() {
        String id = start(guest);
        time.set(time.get().plus(Duration.ofMinutes(15)));
        assertThat(service.get(id, guest).status()).isEqualTo(PlacementAttemptStatus.EXPIRED);
        PlacementAttempt expired = attempts.findById(id).orElseThrow();
        assertThat(expired.getEndedAt()).isEqualTo(expired.getDeadlineAt());
        assertThat(expired.getCorrectCount()).isNull();
        assertThat(expired.getPlacementLevel()).isNull();
        assertThat(service.submit(id, guest).status()).isEqualTo(PlacementAttemptStatus.EXPIRED);
        assertThat(service.start(guest).response().attemptId()).isNotEqualTo(id);
        assertThatThrownBy(() -> service.saveAnswer(id, "q01", "q01-a", guest))
                .isInstanceOfSatisfying(PlacementRequestException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deadlineAutoSubmitsAtDeadlineAndGuestResultBlocksNewAttempt() {
        String id = start(guest);
        service.saveAnswer(id, "q01", "q01-a", guest);
        time.set(time.get().plus(Duration.ofMinutes(17)));
        assertThat(service.submit(id, guest).status()).isEqualTo(PlacementAttemptStatus.AUTO_SUBMITTED);
        PlacementAttempt attempt = attempts.findById(id).orElseThrow();
        assertThat(attempt.getEndedAt()).isEqualTo(attempt.getDeadlineAt());
        assertThat(attempt.getCorrectCount()).isEqualTo(1);
        assertThat(attempt.getClaimExpiresAt()).isEqualTo(attempt.getDeadlineAt().plus(Duration.ofHours(2)));
        assertThat(service.start(guest).response().attemptId()).isEqualTo(id);
        time.set(attempt.getClaimExpiresAt());
        assertThat(service.start(guest).response().attemptId()).isNotEqualTo(id);
    }

    @Test
    void manualSubmitNeedsAnswerAndIsIdempotent() {
        String id = start(guest);
        assertThatThrownBy(() -> service.submit(id, guest)).isInstanceOfSatisfying(PlacementRequestException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(e.code()).isEqualTo("PLACEMENT_NO_ANSWER_TO_SUBMIT");
        });
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        service.saveAnswer(id, "q01", "q01-b", guest);
        var submitted = service.submit(id, guest);
        assertThat(submitted.status()).isEqualTo(PlacementAttemptStatus.SUBMITTED);
        assertThat(submitted.answeredCount()).isEqualTo(1);
        PlacementAttempt finished = attempts.findById(id).orElseThrow();
        assertThat(finished.getCorrectCount()).isZero();
        assertThat(finished.getPlacementLevel()).isEqualTo(PlacementLevel.A1);
        assertThat(finished.getEndedAt()).isEqualTo(time.get());
        time.set(time.get().plus(Duration.ofHours(1)));
        assertThat(service.submit(id, guest)).isEqualTo(submitted);
        assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(finished.getRevision());
        assertThatThrownBy(() -> service.saveAnswer(id, "q01", null, guest)).isInstanceOf(PlacementRequestException.class);
    }

    @Test
    void scoreThresholdsCountAllTwentyQuestionsAndUserResultPreventsRetake() {
        PlacementTestSet set = sets.findPublished().orElseThrow();
        for (int target : List.of(0, 5, 6, 10, 11, 15, 16, 20)) {
            PlacementAccessContext access = new PlacementAccessContext("scoring-" + target, null);
            String id = start(access);
            for (int i = 0; i < Math.max(1, target); i++) {
                var item = set.getItems().get(i);
                String option = i < target ? item.getCorrectOptionId() : item.getOptions().stream()
                        .filter(o -> !o.getId().equals(item.getCorrectOptionId())).findFirst().orElseThrow().getId();
                service.saveAnswer(id, item.getId(), option, access);
            }
            service.submit(id, access);
            PlacementAttempt result = attempts.findById(id).orElseThrow();
            assertThat(result.getCorrectCount()).isEqualTo(target);
            PlacementLevel expected = target <= 5 ? PlacementLevel.A1 : target <= 10 ? PlacementLevel.A2
                    : target <= 15 ? PlacementLevel.B1 : PlacementLevel.B2;
            assertThat(result.getPlacementLevel()).isEqualTo(expected);
            assertThat(service.start(access).response().attemptId()).isEqualTo(id);
        }
    }

    @Test
    void detachedRepositoryCopiesCannotChangeAnswersOrSnapshot() {
        String id = start(guest);
        PlacementAttempt detached = attempts.findById(id).orElseThrow();
        detached.getAnswers().add(new fit.iuh.engolearn.placement.model.vo.PlacementAnswerVo("q01", "q01-a", time.get()));
        sets.findPublished().orElseThrow().getItems().get(0).getOptions().get(0).setText("changed");
        assertThat(attempts.findById(id).orElseThrow().getAnswers()).isEmpty();
        assertThat(sets.findPublished().orElseThrow().getItems().get(0).getOptions().get(0).getText()).isEqualTo("is");
    }

    @Test
    void simultaneousSubmitsFinalizeOnce() throws Exception {
        String id = start(guest);
        service.saveAnswer(id, "q01", "q01-a", guest);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(ignored -> executor.submit(() -> {
                ready.countDown();
                fire.await();
                return service.submit(id, guest);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            assertThat(tasks.get(0).get()).isEqualTo(tasks.get(1).get());
            assertThat(attempts.findById(id).orElseThrow().getRevision()).isEqualTo(2);
        }
    }

    @Test
    void simultaneousStartsShareOneAttemptAndOneDeadline() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(ignored -> executor.submit(() -> {
                ready.countDown();
                fire.await();
                return service.start(guest);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            var first = tasks.get(0).get();
            var second = tasks.get(1).get();
            assertThat(first.response()).isEqualTo(second.response());
            assertThat(first.created() ^ second.created()).isTrue();
        }
    }

    @Test
    void validUserResultWinsOverLegacyActiveAttempt() {
        PlacementAttemptRepository repository = mock(PlacementAttemptRepository.class);
        PlacementAttempt result = PlacementAttempt.builder()
                .id("result-attempt").userId("user-one")
                .status(PlacementAttemptStatus.SUBMITTED)
                .startedAt(time.get()).deadlineAt(time.get().plus(Duration.ofMinutes(15)))
                .endedAt(time.get()).answers(List.of()).correctCount(0)
                .placementLevel(PlacementLevel.A1).build();
        PlacementAttempt legacyActive = PlacementAttempt.builder()
                .id("legacy-active").userId("user-one")
                .status(PlacementAttemptStatus.IN_PROGRESS)
                .startedAt(time.get()).deadlineAt(time.get().plus(Duration.ofMinutes(15)))
                .answers(List.of()).build();
        when(repository.findValidResultForUser("user-one")).thenReturn(Optional.of(result));
        when(repository.findActiveForUser("user-one")).thenReturn(Optional.of(legacyActive));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(time.get());
        PlacementAttemptService legacyService = new PlacementAttemptServiceImpl(repository,
                mock(PlacementTestSetRepository.class), clock, mock(PlacementDeadlineService.class));

        var outcome = legacyService.start(user);

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.response().attemptId()).isEqualTo("result-attempt");
        assertThat(outcome.response().claimRequired()).isFalse();
        verify(repository, never()).findActiveForUser("user-one");
    }

    private String start(PlacementAccessContext access) {
        return service.start(access).response().attemptId();
    }
}
