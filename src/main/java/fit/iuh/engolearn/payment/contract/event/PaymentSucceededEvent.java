package fit.iuh.engolearn.payment.contract.event;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record PaymentSucceededEvent(String eventId, int schemaVersion, Instant occurredAt, String paymentId, String purchaseReference, String userId, long amountVnd, String currency, Instant paidAt) {
    public PaymentSucceededEvent {
        MessageValidation.metadata(eventId, schemaVersion, occurredAt);
        MessageValidation.text(paymentId);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(userId);
        MessageValidation.text(currency);
        if (paidAt == null) throw new IllegalArgumentException("Missing paidAt");
        MessageValidation.money(amountVnd, currency);
    }
}
