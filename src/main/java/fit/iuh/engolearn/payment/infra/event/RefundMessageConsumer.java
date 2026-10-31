package fit.iuh.engolearn.payment.infra.event;
import fit.iuh.engolearn.payment.contract.dto.FullRefundCommandDto;
import fit.iuh.engolearn.payment.repository.PaymentMessageRepository;
import fit.iuh.engolearn.payment.service.RefundService;
import fit.iuh.engolearn.shared.messaging.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component @RequiredArgsConstructor @DependsOn("durableMessageIndexes")
public class RefundMessageConsumer implements MessageConsumer {
    private final PaymentMessageRepository receipts;
    private final RefundService refunds;
    private final MongoMessageTransactions transactions;
    private final ObjectMapper mapper;
    private final Clock clock;
    public String recipient() { return "payment.refund"; }
    public void consume(MessageEnvelopeDto envelope) {
        if (!recipient().equals(envelope.recipient()) || !"FULL_REFUND".equals(envelope.messageType()) || envelope.schemaVersion()!=1)
            throw new IllegalArgumentException("Unsupported refund message");
        var command=mapper.readValue(envelope.payload(),FullRefundCommandDto.class);
        if (!command.messageId().equals(envelope.messageId()) || !command.purchaseReference().equals(envelope.aggregateId()) ||
                !command.occurredAt().equals(envelope.occurredAt())) throw new IllegalArgumentException("Refund envelope mismatch");
        transactions.independent(()->{
            if (receipts.received(envelope)) return null;
            refunds.accept(command,envelope);receipts.recordReceipt(envelope,clock.instant());return null;
        });
    }
}
