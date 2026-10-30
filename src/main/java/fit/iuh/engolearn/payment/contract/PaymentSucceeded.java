package fit.iuh.engolearn.payment.contract;

import java.time.Instant;

/**
 * Payment-owned verified success fact; the future consumer must validate correlation and deduplicate delivery.
 * Field constraints and deferred integration responsibilities are in the payment contract handoff.
 */
public record PaymentSucceeded(
        String eventId,
        String paymentId,
        String purchaseReference,
        String userId,
        long amount,
        String currency,
        Instant paidAt) {}
