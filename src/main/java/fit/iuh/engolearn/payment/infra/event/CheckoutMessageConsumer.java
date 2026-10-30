package fit.iuh.engolearn.payment.infra.event;
import fit.iuh.engolearn.payment.contract.dto.CreateCheckoutCommandDto;
import fit.iuh.engolearn.payment.repository.PaymentMessageRepository;
import fit.iuh.engolearn.payment.service.CheckoutService;
import fit.iuh.engolearn.shared.messaging.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
@Component @RequiredArgsConstructor @DependsOn("durableMessageIndexes")
public class CheckoutMessageConsumer implements MessageConsumer {
    private final PaymentMessageRepository receipts;
    private final CheckoutService checkout;
    private final MongoMessageTransactions transactions;
    private final ObjectMapper mapper;
    private final Clock clock;
    public String recipient() { return "payment.checkout"; }
    public void consume(MessageEnvelopeDto envelope) {
        if (!recipient().equals(envelope.recipient()) || !"CREATE_CHECKOUT".equals(envelope.messageType()))
            throw new IllegalArgumentException("Unsupported checkout message");
        CreateCheckoutCommandDto command = mapper.readValue(envelope.payload(), CreateCheckoutCommandDto.class);
        if (!command.messageId().equals(envelope.messageId()) || !command.purchaseReference().equals(envelope.aggregateId()) ||
                !command.occurredAt().equals(envelope.occurredAt())) throw new IllegalArgumentException("Checkout envelope mismatch");
        transactions.independent(() -> {
            if (receipts.received(envelope)) return null;
            checkout.accept(command, envelope);
            receipts.recordReceipt(envelope, clock.instant());
            return null;
        });
    }
}
