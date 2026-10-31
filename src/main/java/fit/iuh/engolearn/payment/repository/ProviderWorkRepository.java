package fit.iuh.engolearn.payment.repository;
import fit.iuh.engolearn.payment.model.*;
import java.time.Instant;
public interface ProviderWorkRepository {
    PaymentTransaction claimPayment(Instant now, Instant leaseUntil, Instant nextCheck);
    RefundTransaction claimRefund(Instant now, Instant leaseUntil, Instant nextCheck);
    void releasePayment(PaymentTransaction claim, Instant nextCheck);
    void releaseRefund(RefundTransaction claim, Instant nextCheck);
    PaymentTransaction findPayment(String providerReference);
}
