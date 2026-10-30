package fit.iuh.engolearn.payment.repository;

import fit.iuh.engolearn.payment.model.*;
import fit.iuh.engolearn.shared.messaging.repository.MongoDurableMessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoPaymentMessageRepository extends MongoDurableMessageRepository<PaymentOutboxMessage, PaymentMessageReceipt>
        implements PaymentMessageRepository {
    private final MongoTemplate mongo;
    public void review(fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto envelope, String reason) {
        mongo.save(PaymentMessageReview.builder().id(envelope.recipient()+":"+envelope.messageId())
                .messageId(envelope.messageId()).payload(envelope.payload()).reason(reason).build());
    }
    public MongoPaymentMessageRepository(MongoTemplate mongo) {
        super(mongo, PaymentOutboxMessage.class, PaymentMessageReceipt.class,
                PaymentOutboxMessage::new, PaymentMessageReceipt::new, PaymentTransaction.class, RefundTransaction.class, PaymentMessageReview.class);
        this.mongo = mongo;
    }
}
