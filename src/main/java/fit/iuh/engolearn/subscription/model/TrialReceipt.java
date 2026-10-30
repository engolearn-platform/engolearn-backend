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

@Document("subscription_operation_receipts")
@CompoundIndex(name = "user_id_1_operation_1_idempotency_key_1", def = "{'user_id': 1, 'operation': 1, 'idempotency_key': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TrialReceipt extends BaseDocument {
    @Field("user_id")
    private String userId;
    @Field("operation")
    private SubscriptionOperation operation;
    @Field("idempotency_key")
    private String idempotencyKey;
    @Field("fingerprint")
    private String fingerprint;
    @Field("result")
    private TrialActivationVo result;
}
