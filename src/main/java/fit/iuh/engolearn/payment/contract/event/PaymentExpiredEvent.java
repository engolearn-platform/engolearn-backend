package fit.iuh.engolearn.payment.contract.event;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record PaymentExpiredEvent(String eventId, int schemaVersion, Instant occurredAt, String paymentId, String purchaseReference, String userId, Instant expiredAt) {
    public PaymentExpiredEvent {
        MessageValidation.metadata(eventId, schemaVersion, occurredAt);
        MessageValidation.text(paymentId);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(userId);
        if (expiredAt == null) throw new IllegalArgumentException("Missing expiredAt");
    }
}
