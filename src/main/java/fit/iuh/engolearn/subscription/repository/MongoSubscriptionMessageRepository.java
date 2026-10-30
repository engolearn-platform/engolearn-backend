package fit.iuh.engolearn.subscription.repository;

import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.shared.messaging.repository.MongoDurableMessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoSubscriptionMessageRepository extends MongoDurableMessageRepository<SubscriptionOutboxMessage, SubscriptionMessageReceipt>
        implements SubscriptionMessageRepository {
    private final MongoTemplate mongo;
    public void review(fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto envelope, String reason) {
        mongo.save(SubscriptionMessageReview.builder().id(envelope.recipient()+":"+envelope.messageId())
                .messageId(envelope.messageId()).payload(envelope.payload()).reason(reason).build());
    }
    public MongoSubscriptionMessageRepository(MongoTemplate mongo) {
        super(mongo, SubscriptionOutboxMessage.class, SubscriptionMessageReceipt.class,
                SubscriptionOutboxMessage::new, SubscriptionMessageReceipt::new, PremiumPurchase.class, SubscriptionPeriod.class, SubscriptionMessageReview.class);
        this.mongo = mongo;
    }
}
