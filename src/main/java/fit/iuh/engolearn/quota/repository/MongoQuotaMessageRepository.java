package fit.iuh.engolearn.quota.repository;

import fit.iuh.engolearn.quota.model.*;
import fit.iuh.engolearn.shared.messaging.repository.MongoDurableMessageRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoQuotaMessageRepository extends MongoDurableMessageRepository<QuotaOutboxMessage, QuotaMessageReceipt>
        implements QuotaMessageRepository {
    public MongoQuotaMessageRepository(MongoTemplate mongo) {
        super(mongo, QuotaOutboxMessage.class, QuotaMessageReceipt.class,
                QuotaOutboxMessage::new, QuotaMessageReceipt::new);
    }
}
