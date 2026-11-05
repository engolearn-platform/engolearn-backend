package fit.iuh.engolearn.subscription.service;
import fit.iuh.engolearn.subscription.dto.response.RefundResponse;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
public interface SubscriptionRefundService {
    record Requested(RefundResponse refund, boolean created) {}
    Requested request(String actor, String purchaseReference, String key, String reason);
    void apply(MessageEnvelopeDto envelope);
}
