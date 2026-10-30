package fit.iuh.engolearn.payment.contract;

import java.time.Instant;

/**
 * Payment-owned completed full-refund fact; the future consumer must validate correlation and deduplicate delivery.
 * Partial refunds and runtime integration are outside this declaration.
 */
public record PaymentRefundSucceeded(
        String eventId,
        String refundId,
        String paymentId,
        String purchaseReference,
        String userId,
        Instant refundedAt) {}
