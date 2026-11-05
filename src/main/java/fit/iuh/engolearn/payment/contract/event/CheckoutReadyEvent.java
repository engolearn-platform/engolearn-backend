package fit.iuh.engolearn.payment.contract.event;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record CheckoutReadyEvent(String eventId, int schemaVersion, Instant occurredAt, String purchaseReference, String paymentId, String checkoutUrl, Instant expiresAt) {
    public CheckoutReadyEvent {
        MessageValidation.metadata(eventId, schemaVersion, occurredAt);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(paymentId);
        MessageValidation.text(checkoutUrl);
        if (expiresAt == null) throw new IllegalArgumentException("Missing expiresAt");
    }
}
