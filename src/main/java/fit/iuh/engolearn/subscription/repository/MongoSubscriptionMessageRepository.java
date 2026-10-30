package fit.iuh.engolearn.subscription.repository;

import fit.iuh.engolearn.subscription.model.*;
import fit.iuh.engolearn.shared.messaging.repository.MongoDurableMessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoSubscriptionMessageRepository extends MongoDurableMessageRepository<SubscriptionOutboxMessage, SubscriptionMessageReceipt>
        implements SubscriptionMessageRepository {
    public MongoSubscriptionMessageRepository(MongoTemplate mongo) {
        super(mongo, SubscriptionOutboxMessage.class, SubscriptionMessageReceipt.class,
                SubscriptionOutboxMessage::new, SubscriptionMessageReceipt::new, PremiumPurchase.class, SubscriptionPeriod.class);
    }
}
