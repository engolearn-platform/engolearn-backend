package fit.iuh.engolearn.payment.repository;

import fit.iuh.engolearn.payment.model.*;
import fit.iuh.engolearn.shared.messaging.repository.MongoDurableMessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoPaymentMessageRepository extends MongoDurableMessageRepository<PaymentOutboxMessage, PaymentMessageReceipt>
        implements PaymentMessageRepository {
    public MongoPaymentMessageRepository(MongoTemplate mongo) {
        super(mongo, PaymentOutboxMessage.class, PaymentMessageReceipt.class,
                PaymentOutboxMessage::new, PaymentMessageReceipt::new, PaymentTransaction.class, RefundTransaction.class);
    }
}
