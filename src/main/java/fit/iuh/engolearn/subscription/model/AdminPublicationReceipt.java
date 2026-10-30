package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.model.vo.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("subscription_admin_publication_receipts")
@CompoundIndex(name = "actor_id_1_operation_1_idempotency_key_1", def = "{'actor_id': 1, 'operation': 1, 'idempotency_key': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AdminPublicationReceipt extends BaseDocument {
    @Field("actor_id")
    private String actorId;
    @Field("operation")
    private SubscriptionOperation operation;
    @Field("idempotency_key")
    private String idempotencyKey;
    @Field("fingerprint")
    private String fingerprint;
    @Field("accepted_at")
    private Instant acceptedAt;
    @Field("result")
    private PublicationResultVo result;
}
