package fit.iuh.engolearn.quota.model;
import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.quota.model.enums.*;
import fit.iuh.engolearn.quota.model.vo.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document("quota_policy_activation_receipts")
@CompoundIndexes({
    @CompoundIndex(name = "activation_id_1_quota_type_1", def = "{'activation_id': 1, 'quota_type': 1}", unique = true, partialFilter = "{'kind': 'RECEIPT'}"),
    @CompoundIndex(name = "policy_scope_1", def = "{'policy_scope': 1}", unique = true, partialFilter = "{'kind': 'SCOPE_ORDER'}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class QuotaActivationReceipt extends BaseDocument {
    @Field("kind")
    private ActivationKind kind;
    @Field("policy_scope")
    private PolicyScope policyScope;
    @Field("activation_sequence")
    private Long activationSequence;
    @Field("activation_id")
    private String activationId;
    @Field("quota_type")
    private String quotaType;
    @Field("policy_version")
    private Long policyVersion;
    @Field("effective_at")
    private Instant effectiveAt;
    @Field("completed_at")
    private Instant completedAt;
    @Field("state")
    private ActivationState state;
}
