package fit.iuh.engolearn.payment.contract.event;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record PaymentRefundSucceededEvent(String eventId, int schemaVersion, Instant occurredAt, String refundReference, String refundId, String paymentId, String purchaseReference, String userId, Instant refundedAt) {
    public PaymentRefundSucceededEvent {
        MessageValidation.metadata(eventId, schemaVersion, occurredAt);
        MessageValidation.text(refundReference);
        MessageValidation.text(refundId);
        MessageValidation.text(paymentId);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(userId);
        if (refundedAt == null) throw new IllegalArgumentException("Missing refundedAt");
    }
}
