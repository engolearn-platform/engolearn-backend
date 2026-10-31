package fit.iuh.engolearn.payment.repository;
import fit.iuh.engolearn.payment.model.*;
import fit.iuh.engolearn.payment.model.enums.*;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor
public class MongoProviderWorkRepository implements ProviderWorkRepository {
    private final MongoTemplate mongo;
    private Criteria available(Instant now) {
        return new Criteria().andOperator(
                new Criteria().orOperator(Criteria.where("provider_next_check_at").is(null),Criteria.where("provider_next_check_at").lte(now)),
                new Criteria().orOperator(Criteria.where("provider_work_lease_until").is(null),Criteria.where("provider_work_lease_until").lte(now)));
    }
    private Update claim(Instant until,Instant next) {return new Update().set("provider_work_token",UUID.randomUUID().toString())
            .set("provider_work_lease_until",until).set("provider_next_check_at",next);}
    public PaymentTransaction claimPayment(Instant now,Instant until,Instant next) {
        return mongo.findAndModify(Query.query(Criteria.where("status").in(PaymentStatus.CHECKOUT_SETUP_PENDING,PaymentStatus.PENDING,PaymentStatus.UNKNOWN)
                .and("review_reason").is(null).andOperator(available(now),new Criteria().orOperator(Criteria.where("provider").is(null),Criteria.where("provider").is("VNPAY"))))
                .with(org.springframework.data.domain.Sort.by("provider_next_check_at","_id")),claim(until,next),FindAndModifyOptions.options().returnNew(true),PaymentTransaction.class);
    }
    public RefundTransaction claimRefund(Instant now,Instant until,Instant next) {
        return mongo.findAndModify(Query.query(Criteria.where("status").in(RefundStatus.REQUESTED,RefundStatus.PROCESSING,RefundStatus.UNKNOWN)
                .and("review_reason").is(null).andOperator(available(now)))
                .with(org.springframework.data.domain.Sort.by("provider_next_check_at","_id")),claim(until,next),FindAndModifyOptions.options().returnNew(true),RefundTransaction.class);
    }
    public void releasePayment(PaymentTransaction claim,Instant next) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(claim.getId()).and("provider_work_token").is(claim.getProviderWorkToken())),
                release(next),PaymentTransaction.class);
    }
    public void releaseRefund(RefundTransaction claim,Instant next) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(claim.getId()).and("provider_work_token").is(claim.getProviderWorkToken())),
                release(next),RefundTransaction.class);
    }
    private Update release(Instant next) {return new Update().set("provider_next_check_at",next).unset("provider_work_token").unset("provider_work_lease_until");}
    public PaymentTransaction findPayment(String ref) {
        return mongo.findOne(Query.query(Criteria.where("provider").is("VNPAY").and("provider_reference").is(ref)),PaymentTransaction.class);
    }
}
