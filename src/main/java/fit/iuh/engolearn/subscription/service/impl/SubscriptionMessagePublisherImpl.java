package fit.iuh.engolearn.subscription.service.impl;

import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import fit.iuh.engolearn.subscription.contract.event.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionMessageRepository;
import fit.iuh.engolearn.subscription.service.SubscriptionMessagePublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@DependsOn("durableMessageIndexes")
public class SubscriptionMessagePublisherImpl implements SubscriptionMessagePublisher {
    private final SubscriptionMessageRepository messages;
    private final ObjectMapper mapper;
    private final Clock clock;
    public void entitlementChanged(EntitlementChangedEvent event) {
        append("ENTITLEMENT_CHANGED", event.entitlement().userId(), event);
    }
    public void policyActivated(QuotaPolicyActivatedEvent event) {
        append("QUOTA_POLICY_ACTIVATED", event.activationId(), event);
    }
    private void append(String type, String aggregate, Object event) {
        messages.append(new MessageEnvelopeDto(UUID.randomUUID().toString(), type, 1, aggregate,
                "quota.subscription", Instant.ofEpochMilli(clock.millis()), mapper.writeValueAsString(event)));
    }
}
