package fit.iuh.engolearn.payment.repository;
import fit.iuh.engolearn.payment.model.PaymentTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor
public class MongoPaymentTransactionRepository implements PaymentTransactionRepository {
    private final MongoTemplate mongo;
    public PaymentTransaction findByPurchase(String reference) {
        return mongo.findOne(Query.query(Criteria.where("purchase_reference").is(reference)), PaymentTransaction.class);
    }
    public PaymentTransaction findById(String id) { return mongo.findById(id, PaymentTransaction.class); }
    public PaymentTransaction insert(PaymentTransaction payment) { return mongo.insert(payment); }
    public void save(PaymentTransaction payment) { mongo.save(payment); }
    public void guardRefund(String paymentId) {
        if (mongo.updateFirst(Query.query(Criteria.where("_id").is(paymentId)),new Update().inc("refund_revision",1),
                PaymentTransaction.class).getModifiedCount()!=1) throw new IllegalStateException("Payment not found");
    }
    public void markRefundReview(String paymentId,String reason) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(paymentId)),new Update().set("review_reason",reason),PaymentTransaction.class);
    }
}
