package fit.iuh.engolearn.payment.repository;
import fit.iuh.engolearn.payment.model.RefundTransaction;
public interface RefundTransactionRepository {
    RefundTransaction findByReference(String reference);
    RefundTransaction findById(String id);
    RefundTransaction findBlockingOrSucceeded(String paymentId);
    void insert(RefundTransaction refund);
    void save(RefundTransaction refund);
}
