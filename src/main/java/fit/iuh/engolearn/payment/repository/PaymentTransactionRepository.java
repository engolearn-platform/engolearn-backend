package fit.iuh.engolearn.payment.repository;
import fit.iuh.engolearn.payment.model.PaymentTransaction;
public interface PaymentTransactionRepository {
    PaymentTransaction findByPurchase(String reference);
    PaymentTransaction findById(String paymentId);
    PaymentTransaction insert(PaymentTransaction payment);
    void save(PaymentTransaction payment);
    void guardRefund(String paymentId);
    void markRefundReview(String paymentId, String reason);
}
