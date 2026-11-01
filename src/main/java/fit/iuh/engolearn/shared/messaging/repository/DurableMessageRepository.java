package fit.iuh.engolearn.shared.messaging.repository;

import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import fit.iuh.engolearn.shared.messaging.model.OutboxDocument;
import java.time.Instant;
import java.util.Set;

public interface DurableMessageRepository {
    void ensureStorage();
    void append(MessageEnvelopeDto envelope);
    OutboxDocument claim(Set<String> recipients, Instant now, Instant leaseUntil);
    boolean acknowledge(OutboxDocument delivery, Instant now);
    void retry(OutboxDocument delivery, Instant retryAt, String errorCode);
    boolean received(MessageEnvelopeDto envelope);
    void recordReceipt(MessageEnvelopeDto envelope, Instant now);
}
