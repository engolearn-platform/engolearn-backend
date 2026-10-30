package fit.iuh.engolearn.shared.messaging.repository;

import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import fit.iuh.engolearn.shared.messaging.MessageHashes;
import fit.iuh.engolearn.shared.messaging.model.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public abstract class MongoDurableMessageRepository<M extends OutboxDocument, R extends MessageReceiptDocument>
        implements DurableMessageRepository {
    private final MongoTemplate mongo;
    private final Class<M> outboxType;
    private final Class<R> receiptType;
    private final Supplier<M> outboxFactory;
    private final Supplier<R> receiptFactory;
    private final Class<?>[] ownedTypes;

    protected MongoDurableMessageRepository(MongoTemplate mongo, Class<M> outboxType, Class<R> receiptType,
            Supplier<M> outboxFactory, Supplier<R> receiptFactory, Class<?>... ownedTypes) {
        this.mongo = mongo;
        this.outboxType = outboxType;
        this.receiptType = receiptType;
        this.outboxFactory = outboxFactory;
        this.receiptFactory = receiptFactory;
        this.ownedTypes = ownedTypes;
    }

    public void ensureStorage() {
        var resolver = new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        java.util.stream.Stream.concat(java.util.stream.Stream.of(outboxType, receiptType), java.util.Arrays.stream(ownedTypes))
                .forEach(type -> {
                    if (!mongo.collectionExists(type)) mongo.createCollection(type);
                    resolver.resolveIndexFor(type).forEach(index -> mongo.indexOps(type).createIndex(index));
                });
    }

    public void append(MessageEnvelopeDto envelope) {
        requireTransaction();
        M message = outboxFactory.get();
        message.setId(identity(envelope));
        message.setMessageId(envelope.messageId());
        message.setMessageType(envelope.messageType());
        message.setSchemaVersion(envelope.schemaVersion());
        message.setAggregateId(envelope.aggregateId());
        message.setRecipient(envelope.recipient());
        message.setOccurredAt(envelope.occurredAt());
        message.setPayload(envelope.payload());
        message.setStatus(DeliveryStatus.PENDING);
        message.setNextAttemptAt(envelope.occurredAt());
        mongo.insert(message);
    }

    public OutboxDocument claim(Set<String> recipients, Instant now, Instant leaseUntil) {
        if (recipients.isEmpty()) return null;
        Criteria available = new Criteria().orOperator(Criteria.where("status").is(DeliveryStatus.PENDING),
                Criteria.where("status").is(DeliveryStatus.IN_FLIGHT).and("lease_until").lte(now));
        Query query = Query.query(Criteria.where("recipient").in(recipients).and("next_attempt_at").lte(now)
                .andOperator(available)).with(Sort.by("next_attempt_at", "_id"));
        return mongo.findAndModify(query, new Update().set("status", DeliveryStatus.IN_FLIGHT)
                .set("lease_until", leaseUntil).set("lease_token", UUID.randomUUID().toString()).inc("attempt_count", 1),
                FindAndModifyOptions.options().returnNew(true), outboxType);
    }

    public boolean acknowledge(OutboxDocument delivery, Instant now) {
        return mongo.updateFirst(leased(delivery), new Update().set("status", DeliveryStatus.DELIVERED)
                .set("delivered_at", now).unset("lease_token").unset("lease_until").unset("last_error"), outboxType)
                .getModifiedCount() == 1;
    }

    public void retry(OutboxDocument delivery, Instant retryAt, String errorCode) {
        mongo.updateFirst(leased(delivery), new Update().set("status", DeliveryStatus.PENDING)
                .set("next_attempt_at", retryAt).set("last_error", errorCode)
                .unset("lease_token").unset("lease_until"), outboxType);
    }

    public boolean received(MessageEnvelopeDto envelope) {
        requireTransaction();
        R receipt = mongo.findById(identity(envelope), receiptType);
        if (receipt == null) return false;
        if (!MessageHashes.hash(envelope).equals(receipt.getPayloadHash()))
            throw new IllegalArgumentException("Message identity reused with conflicting payload");
        return true;
    }

    public void recordReceipt(MessageEnvelopeDto envelope, Instant now) {
        requireTransaction();
        R receipt = receiptFactory.get();
        receipt.setId(identity(envelope));
        receipt.setMessageId(envelope.messageId());
        receipt.setConsumerId(envelope.recipient());
        receipt.setPayloadHash(MessageHashes.hash(envelope));
        receipt.setProcessedAt(now);
        mongo.insert(receipt);
    }

    private Query leased(OutboxDocument message) {
        return Query.query(Criteria.where("_id").is(message.getId()).and("status").is(DeliveryStatus.IN_FLIGHT)
                .and("lease_token").is(message.getLeaseToken()));
    }

    private String identity(MessageEnvelopeDto envelope) { return envelope.recipient() + ":" + envelope.messageId(); }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Message writes and receipts require a local transaction");
    }
}
