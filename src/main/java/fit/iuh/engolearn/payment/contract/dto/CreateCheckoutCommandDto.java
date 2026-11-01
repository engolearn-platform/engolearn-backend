package fit.iuh.engolearn.payment.contract.dto;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record CreateCheckoutCommandDto(String messageId, int schemaVersion, Instant occurredAt, String purchaseReference, String userId, long amountVnd, String currency, Instant expiresAt, String clientIp) {
    public CreateCheckoutCommandDto(String messageId, int schemaVersion, Instant occurredAt, String purchaseReference,
            String userId, long amountVnd, String currency, Instant expiresAt) {
        this(messageId,schemaVersion,occurredAt,purchaseReference,userId,amountVnd,currency,expiresAt,null);
    }
    public CreateCheckoutCommandDto {
        MessageValidation.metadata(messageId, schemaVersion, occurredAt);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(userId);
        MessageValidation.text(currency);
        if (expiresAt == null) throw new IllegalArgumentException("Missing expiresAt");
        MessageValidation.money(amountVnd, currency);
        if (!expiresAt.isAfter(occurredAt)) throw new IllegalArgumentException("Checkout deadline must follow intent creation");
    }
}
