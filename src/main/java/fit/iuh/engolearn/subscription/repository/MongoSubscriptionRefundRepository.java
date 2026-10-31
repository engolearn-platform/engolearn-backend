package fit.iuh.engolearn.subscription.repository;
import fit.iuh.engolearn.subscription.model.SubscriptionRefundRequest;
import fit.iuh.engolearn.subscription.model.enums.PurchaseRefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor
public class MongoSubscriptionRefundRepository implements SubscriptionRefundRepository {
    private final MongoTemplate mongo;
    public SubscriptionRefundRequest findByKey(String reference, String key) {
        return mongo.findOne(Query.query(Criteria.where("purchase_reference").is(reference).and("idempotency_key").is(key)),SubscriptionRefundRequest.class);
    }
    public SubscriptionRefundRequest findByReference(String reference) {
        return mongo.findOne(Query.query(Criteria.where("refund_reference").is(reference)),SubscriptionRefundRequest.class);
    }
    public SubscriptionRefundRequest findPending(String userId) {
        return mongo.findOne(Query.query(Criteria.where("user_id").is(userId).and("status").in(
                PurchaseRefundStatus.REQUESTED,PurchaseRefundStatus.PROCESSING,PurchaseRefundStatus.UNKNOWN)),SubscriptionRefundRequest.class);
    }
    public void insert(SubscriptionRefundRequest request) { mongo.insert(request); }
    public void save(SubscriptionRefundRequest request) { mongo.save(request); }
}
