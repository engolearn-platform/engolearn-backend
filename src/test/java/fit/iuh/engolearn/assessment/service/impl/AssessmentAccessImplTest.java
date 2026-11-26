package fit.iuh.engolearn.assessment.service.impl;

import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.subscription.contract.EffectiveEntitlementProvider;
import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlementDto;
import fit.iuh.engolearn.subscription.contract.dto.QuotaPolicySnapshotDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssessmentAccessImplTest {
    private static final Instant NOW = Instant.parse("2026-11-24T01:00:00Z");

    @Test
    void managedPaidProductNeedsExplicitFullAnalysisGrant() {
        assertGrant(entitlement("GOLD", "PREMIUM", true, List.of()), false);
        assertGrant(entitlement("GOLD", "PREMIUM", true, List.of("ASSESSMENT_FULL_ANALYSIS")), true);
    }

    @Test
    void legacyPremiumAndTrialKeepTheirExistingFullAnalysisGrant() {
        assertGrant(entitlement("PREMIUM", "PREMIUM", false, List.of()), true);
        assertGrant(entitlement("TRIAL", "TRIAL", false, List.of()), true);
    }

    @Test
    void expiredEntitlementDoesNotGrantFullAnalysis() {
        var expired = new EffectiveEntitlementDto("learner", 1, "PREMIUM", "snapshot-1", 1, 0,
                NOW.minusSeconds(3600), NOW, new QuotaPolicySnapshotDto("snapshot-1", "PREMIUM", 1, List.of()),
                "GOLD", List.of("ASSESSMENT_FULL_ANALYSIS"), true);
        assertGrant(expired, false);
    }

    private EffectiveEntitlementDto entitlement(String planCode, String scope, boolean managed,
            List<String> rights) {
        return new EffectiveEntitlementDto("learner", 1, scope, "snapshot-1", 1, 0, NOW.minusSeconds(1), null,
                new QuotaPolicySnapshotDto("snapshot-1", scope, 1, List.of()), planCode, rights, managed);
    }

    private void assertGrant(EffectiveEntitlementDto entitlement, boolean expected) {
        EffectiveEntitlementProvider provider = (userId, at) -> entitlement;
        AssessmentRepository repository = mock(AssessmentRepository.class);
        when(repository.revoked("learner", "PREMIUM:snapshot-1:" + entitlement.effectiveAt())).thenReturn(false);
        var access = new AssessmentAccessImpl(provider, repository, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(access.currentGrant("learner").isPresent()).isEqualTo(expected);
    }
}
