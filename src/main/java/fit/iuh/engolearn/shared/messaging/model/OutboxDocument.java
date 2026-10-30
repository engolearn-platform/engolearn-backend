package fit.iuh.engolearn.shared.messaging.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.shared.messaging.MessageEnvelopeDto;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public abstract class OutboxDocument extends BaseDocument {
    @Field("message_id") private String messageId;
    @Field("message_type") private String messageType;
    @Field("schema_version") private int schemaVersion;
    @Field("aggregate_id") private String aggregateId;
    @Field("recipient") private String recipient;
    @Field("occurred_at") private Instant occurredAt;
    @Field("payload") private String payload;
    @Field("status") private DeliveryStatus status;
    @Field("attempt_count") private int attemptCount;
    @Field("next_attempt_at") private Instant nextAttemptAt;
    @Field("lease_until") private Instant leaseUntil;
    @Field("lease_token") private String leaseToken;
    @Field("delivered_at") private Instant deliveredAt;
    @Field("last_error") private String lastError;

    public MessageEnvelopeDto envelope() {
        return new MessageEnvelopeDto(messageId, messageType, schemaVersion, aggregateId, recipient, occurredAt, payload);
    }
}
