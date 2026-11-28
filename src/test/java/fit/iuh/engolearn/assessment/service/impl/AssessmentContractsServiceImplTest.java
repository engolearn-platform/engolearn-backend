package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.model.AssessmentAttempt;
import fit.iuh.engolearn.assessment.model.enums.AssessmentSkillBand;
import fit.iuh.engolearn.assessment.model.vo.AssessmentPaperSnapshotVo;
import fit.iuh.engolearn.assessment.model.vo.AssessmentSkillResultVo;
import fit.iuh.engolearn.assessment.contract.enums.AssessmentCompanionScope;
import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.assessment.service.AssessmentAccess;
import fit.iuh.engolearn.assessment.service.AssessmentTransactions;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentContractsServiceImplTest {
    @ParameterizedTest
    @CsvSource({"default,false", "dev,true", "prod,false", "dev+prod,false"})
    void resultReadsPreserveDemonstrationPolicyAndNeverGrantAccess(String profiles, boolean includeDemo) {
        AssessmentRepository store = mock(AssessmentRepository.class);
        AssessmentTransactions transactions = mock(AssessmentTransactions.class);
        AssessmentAccess access = mock(AssessmentAccess.class);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.split("\\+"));
        var service = new AssessmentContractsServiceImpl(store, transactions, environment, access);
        var attempt = attempt();
        when(store.completedAttempt("native-old", "learner", includeDemo)).thenReturn(attempt);
        when(store.completedAttemptIds("learner", Set.of("native-old"), includeDemo)).thenReturn(Set.of("native-old"));
        when(store.valid("learner")).thenReturn(List.of(attempt));

        var result = service.findCompleted("learner", "native-old").orElseThrow();
        assertThat(result.attemptId()).isEqualTo("native-old");
        assertThat(result.paperId()).isEqualTo("paper-v2");
        assertThat(result.skills()).hasSize(5);
        assertThat(result.skills().getFirst().percentage()).isEqualTo(20);
        assertThat(result.skills().getFirst().band().name()).isEqualTo("NEEDS_PRACTICE");
        assertThat(service.findCompletedAttemptIds("learner", Set.of("native-old"))).containsExactly("native-old");
        assertThat(service.latest("learner")).isPresent();
        attempt.getPaper().setDemonstration(true);
        assertThat(service.latest("learner").isPresent()).isEqualTo(includeDemo);
        assertThat(service.latestForCompanion("learner").isPresent()).isEqualTo(includeDemo);
        verify(store, never()).save(any());
        verify(store, never()).ensureGate(any());
        verify(store, never()).revoke(any(), any());
        verifyNoInteractions(transactions);
    }

    @Test
    void invalidIdentitiesAreRejectedBeforePersistence() {
        var store = mock(AssessmentRepository.class);
        var service = new AssessmentContractsServiceImpl(store, mock(AssessmentTransactions.class), new MockEnvironment(), mock(AssessmentAccess.class));
        assertThatThrownBy(() -> service.findCompleted(" ", "native-id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.latestForCompanion(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findCompleted("learner", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findCompletedAttemptIds("learner", Set.of(" "))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void companionGetsOnlyTheAccountVisiblePreviewWithoutWrites() {
        var store = mock(AssessmentRepository.class);
        var transactions = mock(AssessmentTransactions.class);
        var access = mock(AssessmentAccess.class);
        when(store.valid("learner")).thenReturn(List.of(attempt()));
        when(access.currentGrant("learner")).thenReturn(java.util.Optional.empty());
        var service = new AssessmentContractsServiceImpl(store, transactions, new MockEnvironment(), access);

        var result = service.latestForCompanion("learner").orElseThrow();

        assertThat(result.attemptId()).isEqualTo("native-old");
        assertThat(result.submittedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(result.officialScore()).isEqualTo(64);
        assertThat(result.level()).isEqualTo("B1");
        assertThat(result.scope()).isEqualTo(AssessmentCompanionScope.PREVIEW);
        assertThat(result.skills()).extracting("code").containsExactly("R01", "R02");
        verify(store, never()).save(any());
        verify(store, never()).revoke(any(), any());
        verify(store, never()).ensureGate(any());
        verifyNoInteractions(transactions);
    }

    @Test
    void companionFullScopeUsesAnUnrevokedStoredGrant() {
        var store = mock(AssessmentRepository.class);
        var transactions = mock(AssessmentTransactions.class);
        var access = mock(AssessmentAccess.class);
        var attempt = attempt();
        String source = "trial:snapshot:2026-10-01T00:00:00Z";
        attempt.setAnalysisGrants(Set.of(source));
        when(store.valid("learner")).thenReturn(List.of(attempt));
        when(store.revoked("learner", source)).thenReturn(false);
        var service = new AssessmentContractsServiceImpl(store, transactions, new MockEnvironment(), access);

        var result = service.latestForCompanion("learner").orElseThrow();

        assertThat(result.scope()).isEqualTo(AssessmentCompanionScope.FULL);
        assertThat(result.skills()).extracting("code").containsExactly("R01", "R02", "R04", "L01", "L03");
        verifyNoInteractions(access, transactions);
        verify(store, never()).save(any());
    }

    @Test
    void companionCurrentGrantExpandsVisibilityWithoutPersistingItAndRevokedStoredGrantDoesNot() {
        var store = mock(AssessmentRepository.class);
        var transactions = mock(AssessmentTransactions.class);
        var access = mock(AssessmentAccess.class);
        var attempt = attempt();
        String source = "trial:snapshot:2026-10-01T00:00:00Z";
        attempt.setAnalysisGrants(Set.of(source));
        when(store.valid("learner")).thenReturn(List.of(attempt));
        when(store.revoked("learner", source)).thenReturn(true);
        when(access.currentGrant("learner")).thenReturn(java.util.Optional.of("premium:snapshot:2026-11-01T00:00:00Z"));
        var service = new AssessmentContractsServiceImpl(store, transactions, new MockEnvironment(), access);

        var result = service.latestForCompanion("learner").orElseThrow();

        assertThat(result.scope()).isEqualTo(AssessmentCompanionScope.FULL);
        assertThat(result.skills()).hasSize(5);
        assertThat(attempt.getAnalysisGrants()).containsExactly(source);
        verify(store, never()).save(any());
        verify(store, never()).revoke(any(), any());
        verifyNoInteractions(transactions);
    }

    @Test
    void companionReturnsEmptyWhenThereIsNoEligibleReadyResult() {
        var store = mock(AssessmentRepository.class);
        var access = mock(AssessmentAccess.class);
        when(store.valid("learner")).thenReturn(List.of());
        var service = new AssessmentContractsServiceImpl(store, mock(AssessmentTransactions.class), new MockEnvironment(), access);

        assertThat(service.latestForCompanion("learner")).isEmpty();
        verifyNoInteractions(access);
    }

    private AssessmentAttempt attempt() {
        var result = new AssessmentAttempt();
        result.setId("native-old");
        result.setSubmittedAt(Instant.parse("2026-11-01T00:00:00Z"));
        result.setCorrectCount(32);
        result.setLevel("B1");
        result.setPaper(AssessmentPaperSnapshotVo.builder().paperId("paper-v2").build());
        result.setSkillResults(List.of("R01", "R02", "R04", "L01", "L03").stream().map(code ->
                AssessmentSkillResultVo.builder().code(code).name(code + " name").example(code + " example")
                        .correct(2).total(10).percentage(20)
                        .band(AssessmentSkillBand.NEEDS_PRACTICE).build()).toList());
        return result;
    }
}
