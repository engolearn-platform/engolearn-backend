package fit.iuh.engolearn.shared.messaging.model;

import fit.iuh.engolearn.shared.BaseDocument;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public abstract class MessageReceiptDocument extends BaseDocument {
    @Field("message_id") private String messageId;
    @Field("consumer_id") private String consumerId;
    @Field("payload_hash") private String payloadHash;
    @Field("processed_at") private Instant processedAt;
}
