package fit.iuh.engolearn.subscription.dto.response;
import java.time.Instant;
public record RefundResponse(String refundReference, String purchaseReference, String refundId,
        String status, Instant requestedAt, Instant completedAt, String failureCode) {}
