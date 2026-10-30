package fit.iuh.engolearn.payment.contract.event;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record PaymentRefundFailedEvent(String eventId, int schemaVersion, Instant occurredAt, String refundReference, String refundId, String paymentId, String purchaseReference, String userId, String failureCode, Instant failedAt) {
    public PaymentRefundFailedEvent {
        MessageValidation.metadata(eventId, schemaVersion, occurredAt);
        MessageValidation.text(refundReference);
        MessageValidation.text(refundId);
        MessageValidation.text(paymentId);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(userId);
        MessageValidation.text(failureCode);
        if (failedAt == null) throw new IllegalArgumentException("Missing failedAt");
    }
}
