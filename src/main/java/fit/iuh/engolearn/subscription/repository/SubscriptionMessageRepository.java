package fit.iuh.engolearn.subscription.repository;
import fit.iuh.engolearn.shared.messaging.repository.DurableMessageRepository;
public interface SubscriptionMessageRepository extends DurableMessageRepository {
    void review(fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto envelope, String reason);
}
