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

@Document("quota_entitlement_projection")
@CompoundIndexes({
    @CompoundIndex(name = "user_id_1", def = "{'user_id': 1}", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class QuotaEntitlementProjection extends BaseDocument {
    @Field("user_id")
    private String userId;
    @Field("entitlement_version")
    private Long entitlementVersion;
    @Field("policy_scope")
    private PolicyScope policyScope;
    @Field("policy_snapshot_id")
    private String policySnapshotId;
    @Field("policy_version")
    private Long policyVersion;
    @Field("policy_activation_sequence")
    private Long policyActivationSequence;
    @Field("effective_at")
    private Instant effectiveAt;
    @Field("valid_until")
    private Instant validUntil;
    @Field("revision")
    private Long revision;
}
