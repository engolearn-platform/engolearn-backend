package fit.iuh.engolearn.payment.service;
import java.time.Instant;

/** Internal adapter boundary: the provider adapter must establish authoritative outcomes before calling it. */
public interface PaymentOutcomeService {
    void confirmSuccess(String paymentId, long amountVnd, String currency, Instant paidAt);
    void confirmFailure(String paymentId, String failureCode);
    void confirmUnpaidExpiry(String paymentId);
    void markUnknown(String paymentId);
}
