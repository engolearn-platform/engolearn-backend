package fit.iuh.engolearn.assessment.service.impl;
import fit.iuh.engolearn.assessment.service.*;

import fit.iuh.engolearn.assessment.repository.AssessmentRepository;
import fit.iuh.engolearn.subscription.contract.EffectiveEntitlementProvider;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
public class AssessmentAccessImpl implements AssessmentAccess {
    private final EffectiveEntitlementProvider provider;
    private final AssessmentRepository store;
    private final Clock clock;
    public Optional<String> currentGrant(String user) {
        var at=clock.instant();
        var e=provider.resolveEffectiveEntitlement(user,at);
        if(!("PREMIUM".equals(e.policyScope())||"TRIAL".equals(e.policyScope()))
                ||e.effectiveAt().isAfter(at)||e.validUntil()!=null&&!at.isBefore(e.validUntil())) return Optional.empty();
        String source=e.policyScope()+":"+e.policySnapshotId()+":"+e.effectiveAt();
        return store.revoked(user,source)?Optional.empty():Optional.of(source);
    }
}
