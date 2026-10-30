package fit.iuh.engolearn.quota.infra.event;

import fit.iuh.engolearn.quota.service.QuotaActivationService;
import fit.iuh.engolearn.quota.service.QuotaEntitlements;
import fit.iuh.engolearn.subscription.contract.event.EntitlementChangedEvent;
import fit.iuh.engolearn.subscription.contract.event.QuotaPolicyActivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SubscriptionQuotaEventListener {
    private final QuotaEntitlements entitlements;
    private final QuotaActivationService activation;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEntitlementChanged(EntitlementChangedEvent event) { entitlements.onEntitlementChanged(event); }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPolicyActivated(QuotaPolicyActivatedEvent event) { activation.onPolicyActivated(event); }
}
