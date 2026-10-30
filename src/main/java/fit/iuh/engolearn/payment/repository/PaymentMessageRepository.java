package fit.iuh.engolearn.payment.repository;
import fit.iuh.engolearn.shared.messaging.repository.DurableMessageRepository;
public interface PaymentMessageRepository extends DurableMessageRepository {
    void review(fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto envelope, String reason);
}
