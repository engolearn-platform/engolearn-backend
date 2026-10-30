package fit.iuh.engolearn.subscription.contract;

import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlementDto;
import java.time.Instant;

public interface EffectiveEntitlementProvider {
    EffectiveEntitlementDto resolveEffectiveEntitlement(String userId, Instant at);
}
