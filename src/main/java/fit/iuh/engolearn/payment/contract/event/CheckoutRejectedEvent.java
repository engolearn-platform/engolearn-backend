package fit.iuh.engolearn.payment.contract.event;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record CheckoutRejectedEvent(String eventId, int schemaVersion, Instant occurredAt, String purchaseReference, String reasonCode, Instant rejectedAt) {
    public CheckoutRejectedEvent {
        MessageValidation.metadata(eventId, schemaVersion, occurredAt);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(reasonCode);
        if (rejectedAt == null) throw new IllegalArgumentException("Missing rejectedAt");
    }
}
