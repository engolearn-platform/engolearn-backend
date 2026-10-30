package fit.iuh.engolearn.subscription.dto.response;
import java.time.Instant;
public record PurchaseResponse(String purchaseReference, String status, String billingPeriod, long amountVnd,
        String currency, String paymentId, String checkoutUrl, Instant expiresAt, Instant paidAt,
        String periodId, Instant createdAt, String failureCode, Instant periodEndAt) {}
