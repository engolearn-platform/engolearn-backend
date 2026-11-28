package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.contract.dto.*;
import fit.iuh.engolearn.assessment.contract.enums.*;
import fit.iuh.engolearn.assessment.contract.exception.*;
import fit.iuh.engolearn.assessment.model.AssessmentAttempt;
import fit.iuh.engolearn.assessment.model.enums.AssessmentSection;
import fit.iuh.engolearn.assessment.model.vo.*;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.assessment.service.*;
import fit.iuh.engolearn.assessment.utils.AssessmentHistoryCursorCodec;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentCompanionHistoryTest {
    private static final Instant AT = Instant.parse("2026-11-01T00:00:00Z");
    private final AssessmentRepository store = mock(AssessmentRepository.class);
    private final AssessmentTransactions transactions = mock(AssessmentTransactions.class);
    private final AssessmentAccess access = mock(AssessmentAccess.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final AssessmentContractsServiceImpl service =
            new AssessmentContractsServiceImpl(store, transactions, environment, access);

    @Test
    void returnsMixedVisibilityAndRechecksRevocationAndCurrentGrantWithoutWrites() {
        var full = attempt("full", Set.of("valid"));
        var preview = attempt("preview", Set.of("revoked"));
        when(store.revoked("owner", "valid")).thenReturn(false);
        when(store.revoked("owner", "revoked")).thenReturn(true);
        when(access.currentGrant("owner")).thenReturn(Optional.empty(), Optional.of("current"));
        when(store.companionHistory("owner", false, null, null, 3)).thenReturn(List.of(full, preview));

        var page = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, null));
        assertThat(page.coverage()).isEqualTo(AssessmentCompanionHistoryCoverage.COMPLETE);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.asOf()).isNotNull();
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().getFirst().result().scope()).isEqualTo(AssessmentCompanionScope.FULL);
        assertThat(page.items().getFirst().result().skills()).hasSize(5);
        assertThat(page.items().getLast().result().scope()).isEqualTo(AssessmentCompanionScope.PREVIEW);
        assertThat(page.items().getLast().result().skills()).extracting("code").containsExactly("R01", "R02");
        assertThat(page.items().getLast().paperId()).isEqualTo("paper");
        assertThat(page.items().getLast().paperVersion()).isEqualTo(2);
        assertThat(page.items().getLast().rulesVersion()).isEqualTo(3);
        assertThat(page.items().getLast().result().officialScore()).isEqualTo(64);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);

        var nextRead = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, null));
        assertThat(nextRead.items().getLast().result().scope()).isEqualTo(AssessmentCompanionScope.FULL);
        assertThat(preview.getAnalysisGrants()).containsExactly("revoked");
        verify(store, times(2)).revoked("owner", "revoked");
        verify(access, times(2)).currentGrant("owner");
        verify(store, never()).save(any());
        verify(store, never()).revoke(any(), any());
        verify(store, never()).ensureGate(any());
        verifyNoInteractions(transactions);
    }

    @Test
    void pageUsesLastReturnedRowNotLookaheadAndRevalidatesAnchor() {
        when(store.companionHistory("owner", false, null, null, 3))
                .thenReturn(List.of(attempt("z", Set.of()), attempt("y", Set.of()), attempt("x", Set.of())));
        var first = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, null));
        assertThat(first.hasMore()).isTrue();
        assertThat(first.items()).hasSize(2);
        verify(access, times(2)).currentGrant("owner");
        when(store.companionHistoryAnchorExists("owner", false, AT, "y")).thenReturn(true);
        when(store.companionHistory("owner", false, AT, "y", 3)).thenReturn(List.of(attempt("x", Set.of())));
        var second = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, first.nextCursor()));
        assertThat(second.items()).extracting(row -> row.result().attemptId()).containsExactly("x");
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextCursor()).isNull();
        assertThatThrownBy(() -> service.historyForCompanion("foreign", new AssessmentCompanionHistoryQueryDto(2, first.nextCursor())))
                .isInstanceOf(AssessmentHistoryInvalidQueryException.class);
        assertThatThrownBy(() -> service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(3, first.nextCursor())))
                .isInstanceOf(AssessmentHistoryInvalidQueryException.class);
        var forged = AssessmentHistoryCursorCodec.encode("owner", false, 2,
                new AssessmentHistoryCursorCodec.Position(AT, "foreign-or-missing"));
        assertThatThrownBy(() -> service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, forged)))
                .isInstanceOf(AssessmentHistoryInvalidQueryException.class);
        verify(store, never()).companionHistory("foreign", false, AT, "y", 3);
        verify(store, never()).companionHistory("owner", false, AT, "foreign-or-missing", 3);
        verifyNoInteractions(transactions);
    }

    @Test
    void invalidArgumentsNeverReadAndGrantFailureIsNotPreview() {
        for (var query : List.of(new AssessmentCompanionHistoryQueryDto(1, null),
                new AssessmentCompanionHistoryQueryDto(6, null), new AssessmentCompanionHistoryQueryDto(2, ""),
                new AssessmentCompanionHistoryQueryDto(2, "x".repeat(513)), new AssessmentCompanionHistoryQueryDto(2, "bad"))) {
            assertThatThrownBy(() -> service.historyForCompanion("owner", query))
                    .isInstanceOf(AssessmentHistoryInvalidQueryException.class);
        }
        assertThatThrownBy(() -> service.historyForCompanion(" ", new AssessmentCompanionHistoryQueryDto(2, null)))
                .isInstanceOf(AssessmentHistoryInvalidQueryException.class);
        assertThatThrownBy(() -> service.historyForCompanion("owner", null))
                .isInstanceOf(AssessmentHistoryInvalidQueryException.class);
        verifyNoInteractions(store, transactions, access);
        when(store.companionHistory("owner", false, null, null, 3)).thenReturn(List.of(attempt("a", Set.of())));
        when(access.currentGrant("owner")).thenThrow(new IllegalStateException("internal canary"));
        assertThatThrownBy(() -> service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(2, null)))
                .isInstanceOf(AssessmentHistoryUnavailableException.class).hasMessage("Assessment history unavailable");
        verifyNoInteractions(transactions);
    }

    @ParameterizedTest
    @CsvSource({"default,false", "dev,true", "prod,false", "dev+prod,false"})
    void demonstrationPolicyMatchesLatestAndEmptyIsComplete(String profiles, boolean includeDemo) {
        environment.setActiveProfiles(profiles.split("\\+"));
        when(store.companionHistory("owner", includeDemo, null, null, 6)).thenReturn(List.of());
        var page = service.historyForCompanion("owner", new AssessmentCompanionHistoryQueryDto(5, null));
        assertThat(page.items()).isEmpty();
        assertThat(page.coverage()).isEqualTo(AssessmentCompanionHistoryCoverage.COMPLETE);
        assertThat(page.hasMore()).isFalse();
        verify(store).companionHistory("owner", includeDemo, null, null, 6);
        verifyNoInteractions(access, transactions);
    }

    private AssessmentAttempt attempt(String id, Set<String> grants) {
        var result = new AssessmentAttempt();
        result.setId(id);
        result.setSubmittedAt(AT);
        result.setCorrectCount(32);
        result.setLevel("B1");
        result.setPaper(AssessmentPaperSnapshotVo.builder().paperId("paper").version(2).rulesVersion(3).build());
        result.setAnalysisGrants(grants);
        result.setSkillResults(List.of("R01", "R02", "R03", "L01", "L02").stream()
                .map(code -> AssessmentSkillResultVo.builder().code(code).section(code.startsWith("R")
                        ? AssessmentSection.READING : AssessmentSection.LISTENING)
                        .correct(2).total(10).percentage(20).build()).toList());
        return result;
    }
}