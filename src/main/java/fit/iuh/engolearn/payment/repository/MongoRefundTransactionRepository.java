package fit.iuh.engolearn.payment.repository;
import fit.iuh.engolearn.payment.model.RefundTransaction;
import fit.iuh.engolearn.payment.model.enums.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor
public class MongoRefundTransactionRepository implements RefundTransactionRepository {
    private final MongoTemplate mongo;
    public RefundTransaction findByReference(String reference) {
        return mongo.findOne(Query.query(Criteria.where("refund_reference").is(reference)),RefundTransaction.class);
    }
    public RefundTransaction findById(String id) { return mongo.findById(id,RefundTransaction.class); }
    public RefundTransaction findBlockingOrSucceeded(String paymentId) {
        return mongo.findOne(Query.query(Criteria.where("payment_id").is(paymentId).andOperator(new Criteria().orOperator(
                Criteria.where("blocks_new_refund").is(true),Criteria.where("status").is(RefundStatus.SUCCEEDED)))),RefundTransaction.class);
    }
    public void insert(RefundTransaction refund) { mongo.insert(refund); }
    public void save(RefundTransaction refund) { mongo.save(refund); }
}
