package fit.iuh.engolearn.payment.contract.event;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record PaymentFailedEvent(String eventId, int schemaVersion, Instant occurredAt, String paymentId, String purchaseReference, String userId, String failureCode, Instant failedAt) {
    public PaymentFailedEvent {
        MessageValidation.metadata(eventId, schemaVersion, occurredAt);
        MessageValidation.text(paymentId);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(userId);
        MessageValidation.text(failureCode);
        if (failedAt == null) throw new IllegalArgumentException("Missing failedAt");
    }
}
