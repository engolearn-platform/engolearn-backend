package fit.iuh.engolearn.quota.infra.event;

import fit.iuh.engolearn.shared.messaging.*;
import fit.iuh.engolearn.quota.repository.QuotaMessageRepository;
import fit.iuh.engolearn.quota.service.QuotaActivationService;
import fit.iuh.engolearn.quota.service.QuotaEntitlements;
import fit.iuh.engolearn.subscription.contract.event.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@DependsOn("durableMessageIndexes")
public class SubscriptionQuotaMessageConsumer implements MessageConsumer {
    private final QuotaMessageRepository receipts;
    private final MongoMessageTransactions transactions;
    private final QuotaEntitlements entitlements;
    private final QuotaActivationService activation;
    private final ObjectMapper mapper;
    private final Clock clock;
    public String recipient() { return "quota.subscription"; }
    public void consume(MessageEnvelopeDto envelope) {
        if (!recipient().equals(envelope.recipient()) || envelope.schemaVersion() != 1)
            throw new IllegalArgumentException("Unsupported recipient or message version");
        transactions.independent(() -> {
            if (receipts.received(envelope)) return null;
            switch (envelope.messageType()) {
                case "ENTITLEMENT_CHANGED" -> {
                    EntitlementChangedEvent event = mapper.readValue(envelope.payload(), EntitlementChangedEvent.class);
                    if (!envelope.aggregateId().equals(event.entitlement().userId()))
                        throw new IllegalArgumentException("Entitlement correlation mismatch");
                    entitlements.onEntitlementChanged(event);
                }
                case "QUOTA_POLICY_ACTIVATED" -> {
                    QuotaPolicyActivatedEvent event = mapper.readValue(envelope.payload(), QuotaPolicyActivatedEvent.class);
                    if (!envelope.aggregateId().equals(event.activationId()))
                        throw new IllegalArgumentException("Policy activation correlation mismatch");
                    activation.onPolicyActivated(event);
                }
                default -> throw new IllegalArgumentException("Unsupported Subscription message type");
            }
            receipts.recordReceipt(envelope, clock.instant());
            return null;
        });
    }
}
