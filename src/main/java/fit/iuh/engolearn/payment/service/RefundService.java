package fit.iuh.engolearn.payment.service;
import fit.iuh.engolearn.payment.contract.dto.FullRefundCommandDto;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import java.time.Instant;
public interface RefundService {
    void accept(FullRefundCommandDto command, MessageEnvelopeDto envelope);
    boolean startProcessing(String refundId);
    void markUnknown(String refundId);
    void confirmSuccess(String refundId, long amountVnd, String currency, Instant refundedAt);
    void confirmFailure(String refundId, String failureCode, Instant failedAt);
}
