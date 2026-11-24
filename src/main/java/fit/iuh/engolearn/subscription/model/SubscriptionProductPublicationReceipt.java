package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("subscription_product_publication_receipts")
@CompoundIndex(name = "product_publication_actor_key_unique", def = "{'actor_id':1,'idempotency_key':1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class SubscriptionProductPublicationReceipt extends BaseDocument {
    @Field("actor_id") private String actorId;
    @Field("idempotency_key") private String idempotencyKey;
    @Field("plan_code") private String planCode;
    @Field("fingerprint") private String fingerprint;
    @Field("version") private long version;
    @Field("accepted_at") private Instant acceptedAt;
}
