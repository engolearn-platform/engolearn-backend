package fit.iuh.engolearn.payment.contract.dto;

import fit.iuh.engolearn.shared.messaging.MessageValidation;
import java.time.Instant;

public record FullRefundCommandDto(String messageId, int schemaVersion, Instant occurredAt, String refundReference, String paymentId, String purchaseReference, String userId, String requestedBy, String reason) {
    public FullRefundCommandDto {
        MessageValidation.metadata(messageId, schemaVersion, occurredAt);
        MessageValidation.text(refundReference);
        MessageValidation.text(paymentId);
        MessageValidation.text(purchaseReference);
        MessageValidation.text(userId);
        MessageValidation.text(requestedBy);
        MessageValidation.text(reason);
    }
}
