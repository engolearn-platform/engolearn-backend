package fit.iuh.engolearn.assessment.infra.event;

import fit.iuh.engolearn.assessment.contract.AssessmentGrantRevocation;
import fit.iuh.engolearn.assessment.service.AssessmentAccess;
import fit.iuh.engolearn.shared.messaging.MessageConsumer;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import fit.iuh.engolearn.subscription.contract.event.PremiumPeriodRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AssessmentRefundConsumer implements MessageConsumer {
    private final AssessmentGrantRevocation revocation;
    private final ObjectMapper mapper;

    @Override
    public String recipient() {
        return "assessment.subscription";
    }

    @Override
    public void consume(MessageEnvelopeDto envelope) {
        if (!recipient().equals(envelope.recipient()) || envelope.schemaVersion() != 1
                || !"PREMIUM_PERIOD_REFUNDED".equals(envelope.messageType())) {
            throw new IllegalArgumentException("Unsupported Assessment refund message");
        }
        var event = mapper.readValue(envelope.payload(), PremiumPeriodRefundedEvent.class);
        if (!envelope.aggregateId().equals(event.userId()) || event.policySnapshotId() == null
                || event.policySnapshotId().isBlank() || event.effectiveAt() == null) {
            throw new IllegalArgumentException("Invalid Assessment refund correlation");
        }
        revocation.revoke(event.userId(), AssessmentAccess.sourceKey("PREMIUM", event.policySnapshotId(), event.effectiveAt()));
    }
}
