package fit.iuh.engolearn.subscription.service;

import fit.iuh.engolearn.subscription.contract.event.EntitlementChangedEvent;
import fit.iuh.engolearn.subscription.contract.event.QuotaPolicyActivatedEvent;

public interface SubscriptionMessagePublisher {
    void entitlementChanged(EntitlementChangedEvent event);
    void policyActivated(QuotaPolicyActivatedEvent event);
}
