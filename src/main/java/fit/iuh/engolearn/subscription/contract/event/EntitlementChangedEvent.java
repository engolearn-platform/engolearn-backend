package fit.iuh.engolearn.subscription.contract.event;

import fit.iuh.engolearn.subscription.contract.dto.EffectiveEntitlementDto;

public record EntitlementChangedEvent(EffectiveEntitlementDto entitlement) {}
