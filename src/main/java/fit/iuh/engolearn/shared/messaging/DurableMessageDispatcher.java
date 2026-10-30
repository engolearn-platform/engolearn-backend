package fit.iuh.engolearn.shared.messaging;

import fit.iuh.engolearn.shared.messaging.model.OutboxDocument;
import fit.iuh.engolearn.shared.messaging.repository.DurableMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DurableMessageDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(DurableMessageDispatcher.class);
    private final List<DurableMessageRepository> outboxes;
    private final Map<String, MessageConsumer> consumers;
    private final Clock clock;
    private final Duration lease;
    private final Duration backoff;
    private final int batchSize;

    public DurableMessageDispatcher(List<DurableMessageRepository> outboxes, List<MessageConsumer> consumers,
            Clock clock, Duration lease, Duration backoff, int batchSize) {
        if (lease.isNegative() || lease.isZero() || backoff.isNegative() || backoff.isZero() || batchSize < 1)
            throw new IllegalArgumentException("Invalid delivery bounds");
        this.outboxes = List.copyOf(outboxes);
        Map<String, MessageConsumer> routes = new HashMap<>();
        for (MessageConsumer consumer : consumers)
            if (routes.putIfAbsent(consumer.recipient(), consumer) != null)
                throw new IllegalArgumentException("Duplicate message recipient");
        this.consumers = Map.copyOf(routes);
        this.clock = clock;
        this.lease = lease;
        this.backoff = backoff;
        this.batchSize = batchSize;
    }

    public int dispatch() {
        int delivered = 0;
        for (DurableMessageRepository outbox : outboxes) {
            for (int attempt = 0; attempt < batchSize; attempt++) {
                Instant now = clock.instant();
                OutboxDocument message = outbox.claim(consumers.keySet(), now, now.plus(lease));
                if (message == null) break;
                try {
                    consumers.get(message.getRecipient()).consume(message.envelope());
                    if (outbox.acknowledge(message, clock.instant())) delivered++;
                } catch (RuntimeException failure) {
                    long multiplier = 1L << Math.min(6, Math.max(0, message.getAttemptCount() - 1));
                    outbox.retry(message, clock.instant().plus(backoff.multipliedBy(multiplier)),
                            failure.getClass().getSimpleName());
                    LOG.warn("Message delivery failed: id={}, recipient={}, attempt={}, error={}",
                            message.getMessageId(), message.getRecipient(), message.getAttemptCount(),
                            failure.getClass().getSimpleName());
                }
            }
        }
        return delivered;
    }
}
