package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminAttemptSearchRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptSummaryResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.PlacementTestSet;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import fit.iuh.engolearn.placement.model.vo.PlacementAnswerVo;
import fit.iuh.engolearn.placement.model.vo.PlacementOptionVo;
import fit.iuh.engolearn.placement.model.vo.PlacementSnapshotItemVo;
import fit.iuh.engolearn.placement.repository.InMemoryPlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementAttemptTestSetRepository;
import fit.iuh.engolearn.placement.repository.PlacementAttemptTestSetVersion;
import fit.iuh.engolearn.placement.repository.PlacementTestSetRepository;
import fit.iuh.engolearn.placement.service.impl.PlacementAdminAttemptServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacementAdminAttemptServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-19T17:00:00Z");
    private final InMemoryPlacementAttemptRepository attempts = new InMemoryPlacementAttemptRepository();
    private final ArchivedTestSets testSets = new ArchivedTestSets();
    private final PlacementAdminAttemptService service =
            new PlacementAdminAttemptServiceImpl(attempts, testSets, testSets, Clock.fixed(NOW, ZoneOffset.UTC), source -> source);

    @Test
    void inProgressAndExpiredKeepAnswersWithoutKeysOrScoresAndDoNotResolveTheDeadline() {
        save("guest-in-progress", null, "guest-token-secret", PlacementAttemptStatus.IN_PROGRESS,
                NOW.minusSeconds(3600), 3);
        save("guest-expired", null, "other-guest-token", PlacementAttemptStatus.EXPIRED, NOW.minusSeconds(3600), 3);

        for (String id : List.of("guest-in-progress", "guest-expired")) {
            var detail = service.getDetail(id);

            assertThat(detail.name()).startsWith("Khách #").doesNotContain("guest-token");
            assertThat(detail.userId()).isNull();
            assertThat(detail.answers()).containsEntry("q-first", "q-first-a");
            assertThat(detail.correctCount()).isNull();
            assertThat(detail.level()).isNull();
            assertThat(detail.snapshot()).extracting(question -> question.correctOptionId()).containsOnly("");
            assertThat(detail.snapshot()).extracting(question -> question.explanation()).containsOnly("");
            assertThat(detail.snapshot()).extracting(question -> question.transcript()).containsOnly("");
        }
        assertThat(attempts.findById("guest-in-progress").orElseThrow().getStatus())
                .isEqualTo(PlacementAttemptStatus.IN_PROGRESS);
        assertThat(attempts.findById("guest-in-progress").orElseThrow().getRevision()).isEqualTo(7);
    }

    @Test
    void submittedAttemptsUseAssignedArchivedSnapshotAndPersistedZeroScore() {
        save("claimed", null, "guest-token-secret", PlacementAttemptStatus.AUTO_SUBMITTED,
                NOW.minusSeconds(10), 0);
        attempts.claimIfEligible("claimed", 8, "guest-token-secret", "member-1", NOW).orElseThrow();

        var detail = service.getDetail("claimed");

        assertThat(detail.kind().name()).isEqualTo("USER");
        assertThat(detail.userId()).isEqualTo("member-1");
        assertThat(detail.claimStatus()).isEqualTo(PlacementClaimStatus.ALREADY_CLAIMED);
        assertThat(detail.testSetCode()).isEqualTo("PLACEMENT");
        assertThat(detail.testSetVersion()).isEqualTo(2);
        assertThat(detail.deadlineAt()).isEqualTo(NOW.plusSeconds(590));
        assertThat(detail.endedAt()).isEqualTo(NOW.minusSeconds(9));
        assertThat(detail.correctCount()).isZero();
        assertThat(detail.level()).isEqualTo(PlacementLevel.A1);
        assertThat(detail.totalQuestions()).isEqualTo(2);
        assertThat(detail.snapshot()).extracting(question -> question.id()).containsExactly("q-first", "q-second");
        assertThat(detail.snapshot()).extracting(question -> question.code()).containsExactly("R-A1-01", "q-second");
        assertThat(detail.snapshot().getFirst().correctOptionId()).isEqualTo("q-first-a");
        assertThat(detail.snapshot().getFirst().status()).isEqualTo(PlacementQuestionStatus.ACTIVE);
    }

    @Test
    void filtersCombineAndCalendarDatesIncludeBothBoundariesInVietnam() {
        save("before-day", "member-before", null, PlacementAttemptStatus.SUBMITTED,
                Instant.parse("2026-11-18T16:59:59Z"), 0);
        save("at-start", "member-1", null, PlacementAttemptStatus.SUBMITTED,
                Instant.parse("2026-11-18T17:00:00Z"), 0);
        save("wrong-user", "member-2", null, PlacementAttemptStatus.SUBMITTED, NOW.minusSeconds(1), 0);
        save("other-status", "member-3", null, PlacementAttemptStatus.EXPIRED, NOW.minusSeconds(1), 0);
        save("at-next-day", "member-next-day", null, PlacementAttemptStatus.SUBMITTED, NOW, 0);
        var day = LocalDate.parse("2026-11-19");
        var request = new PlacementAdminAttemptSearchRequest(0, 1, "member-1", "member-1", "placement", 2,
                PlacementAttemptStatus.SUBMITTED, PlacementLevel.A1, day, day);

        var page = service.search(request);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.items()).extracting(PlacementAdminAttemptSummaryResponse::id).containsExactly("at-start");
        assertThat(page.items().getFirst().totalQuestions()).isEqualTo(2);
        assertThat(service.search(new PlacementAdminAttemptSearchRequest(0, 10, null, null, null, null, null, null,
                day, day)).items()).extracting(PlacementAdminAttemptSummaryResponse::id)
                .containsExactly("other-status", "wrong-user", "at-start");
    }

    @Test
    void paginationHasStableTieBreakAndSearchRejectsPartialAttemptIds() {
        save("try-two", null, "guest-two", PlacementAttemptStatus.EXPIRED, NOW, null);
        save("try-one", null, "guest-one", PlacementAttemptStatus.EXPIRED, NOW, null);

        var secondPage = service.search(new PlacementAdminAttemptSearchRequest(1, 1, null, null, null, null,
                null, null, null, null));
        var pastLastPage = service.search(new PlacementAdminAttemptSearchRequest(2, 1, null, null, null, null,
                null, null, null, null));
        var partialMatch = service.search(new PlacementAdminAttemptSearchRequest(0, 10, "try-", null, null, null,
                null, null, null, null));
        var exactMatch = service.search(new PlacementAdminAttemptSearchRequest(0, 10, "try-one", null, null, null,
                null, null, null, null));

        assertThat(secondPage.total()).isEqualTo(2);
        assertThat(secondPage.items()).extracting(PlacementAdminAttemptSummaryResponse::id).containsExactly("try-two");
        assertThat(pastLastPage.total()).isEqualTo(2);
        assertThat(pastLastPage.page()).isEqualTo(2);
        assertThat(pastLastPage.items()).isEmpty();
        assertThat(partialMatch.items()).isEmpty();
        assertThat(exactMatch.items()).extracting(PlacementAdminAttemptSummaryResponse::id).containsExactly("try-one");
    }

    @Test
    void missingAttemptAndReversedDateRangeReturnTypedErrors() {
        assertThatThrownBy(() -> service.getDetail("missing"))
                .isInstanceOfSatisfying(PlacementRequestException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.search(new PlacementAdminAttemptSearchRequest(0, 10, null, null, null, null,
                null, null, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-19"))))
                .isInstanceOfSatisfying(PlacementRequestException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void save(String id, String user, String guest, PlacementAttemptStatus status, Instant started,
                      Integer score) {
        PlacementAttempt attempt = PlacementAttempt.builder().id(id).userId(user).guestSessionId(guest)
                .testSetId("archived-set-v2").testSetVersion(2).status(PlacementAttemptStatus.IN_PROGRESS)
                .startedAt(started).deadlineAt(started.plusSeconds(600)).revision(7)
                .answers(List.of(new PlacementAnswerVo("q-first", "q-first-a", started)))
                .build();
        if (status == PlacementAttemptStatus.IN_PROGRESS) {
            attempt.setCorrectCount(score);
            attempt.setPlacementLevel(PlacementLevel.A1);
            attempts.startIfEligible(attempt, guest, started);
        } else {
            attempts.startIfEligible(attempt, guest, started);
            attempts.finish(id, 7, status, started.plusSeconds(1),
                    score, PlacementLevel.A1, started.plusSeconds(3600)).orElseThrow();
        }
    }

    private static class ArchivedTestSets implements PlacementTestSetRepository, PlacementAttemptTestSetRepository {
        private final PlacementTestSet archivedSet = PlacementTestSet.builder().id("archived-set-v2").version(2)
                .code("PLACEMENT").status(PlacementTestSetStatus.ARCHIVED)
                .items(List.of(item("q-second", 2), item("q-first", 1))).build();
        private final PlacementAttemptTestSetVersion summary =
                new PlacementAttemptTestSetVersion("archived-set-v2", 2, "PLACEMENT", 2);

        @Override
        public Optional<PlacementTestSet> findPublished() {
            return Optional.empty();
        }

        @Override
        public Optional<PlacementTestSet> findVersion(String id, int version) {
            return archivedSet.getId().equals(id) && version == 2 ? Optional.of(archivedSet) : Optional.empty();
        }

        @Override
        public List<PlacementAttemptTestSetVersion> findVersionsByCode(String code, Integer version) {
            return "PLACEMENT".equalsIgnoreCase(code) && (version == null || version == 2)
                    ? List.of(summary) : List.of();
        }

        @Override
        public List<PlacementAttemptTestSetVersion> findVersionsById(Set<String> ids) {
            return ids.contains(summary.id()) ? List.of(summary) : List.of();
        }

        private static PlacementSnapshotItemVo item(String id, int order) {
            return PlacementSnapshotItemVo.builder().id(id).code(order == 1 ? "R-A1-01" : null)
                    .order(order).skill(PlacementSkill.READING)
                    .level(PlacementLevel.A1).prompt("Prompt").passage("Passage")
                    .options(List.of(new PlacementOptionVo(id + "-a", "A", "Option")))
                    .correctOptionId(id + "-a").explanation("Explanation").transcript("Transcript").build();
        }
    }
}
