package fit.iuh.engolearn.subscription.contract;

import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlement;
import java.time.Instant;

public interface EffectiveEntitlementProvider {
    EffectiveEntitlement resolveEffectiveEntitlement(String userId, Instant at);
}
