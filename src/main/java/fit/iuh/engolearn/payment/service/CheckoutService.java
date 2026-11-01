package fit.iuh.engolearn.payment.service;
import fit.iuh.engolearn.payment.contract.dto.CreateCheckoutCommandDto;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import java.time.Instant;
public interface CheckoutService {
    void accept(CreateCheckoutCommandDto command, MessageEnvelopeDto envelope);
    void prepared(String paymentId, String checkoutUrl, Instant expiresAt);
}
