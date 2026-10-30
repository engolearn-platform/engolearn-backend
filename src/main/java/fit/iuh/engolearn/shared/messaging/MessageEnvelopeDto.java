package fit.iuh.engolearn.shared.messaging;

import java.time.Instant;

public record MessageEnvelopeDto(String messageId, String messageType, int schemaVersion,
        String aggregateId, String recipient, Instant occurredAt, String payload) {
    public MessageEnvelopeDto {
        MessageValidation.metadata(messageId, schemaVersion, occurredAt);
        MessageValidation.text(messageType);
        MessageValidation.text(aggregateId);
        MessageValidation.text(recipient);
        MessageValidation.text(payload);
    }
}
