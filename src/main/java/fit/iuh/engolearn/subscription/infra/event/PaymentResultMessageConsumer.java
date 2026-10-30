package fit.iuh.engolearn.subscription.infra.event;
import fit.iuh.engolearn.shared.messaging.*;
import fit.iuh.engolearn.subscription.repository.SubscriptionMessageRepository;
import fit.iuh.engolearn.subscription.service.PurchaseResultService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @DependsOn("durableMessageIndexes")
public class PaymentResultMessageConsumer implements MessageConsumer {
    private final SubscriptionMessageRepository receipts;
    private final PurchaseResultService results;
    private final MongoMessageTransactions transactions;
    private final Clock clock;
    public String recipient() { return "subscription.payment"; }
    public void consume(MessageEnvelopeDto envelope) {
        if (!recipient().equals(envelope.recipient()) || envelope.schemaVersion() != 1)
            throw new IllegalArgumentException("Unsupported Payment result envelope");
        transactions.independent(() -> {
            if (receipts.received(envelope)) return null;
            results.apply(envelope);
            receipts.recordReceipt(envelope, clock.instant());
            return null;
        });
    }
}
