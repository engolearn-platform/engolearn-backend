package fit.iuh.engolearn.subscription.repository;
import fit.iuh.engolearn.subscription.model.SubscriptionRefundRequest;
public interface SubscriptionRefundRepository {
    SubscriptionRefundRequest findByKey(String purchaseReference, String key);
    SubscriptionRefundRequest findByReference(String reference);
    SubscriptionRefundRequest findPending(String userId);
    void insert(SubscriptionRefundRequest request);
    void save(SubscriptionRefundRequest request);
}
