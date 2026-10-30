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

@Document("quota_policy_snapshots")
@CompoundIndexes({
    @CompoundIndex(name = "policy_snapshot_id_1", def = "{'policy_snapshot_id': 1}", unique = true),
    @CompoundIndex(name = "policy_scope_1_policy_version_1", def = "{'policy_scope': 1, 'policy_version': 1}", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class QuotaPolicySnapshot extends BaseDocument {
    @Field("policy_snapshot_id")
    private String policySnapshotId;
    @Field("policy_scope")
    private PolicyScope policyScope;
    @Field("policy_version")
    private Long policyVersion;
    @Field("timezone")
    private String timezone;
    @Field("rules")
    @Singular
    private List<QuotaRuleVo> rules;
    public List<QuotaRuleVo> getRules() { return rules == null ? null : List.copyOf(rules); }
    public void setRules(List<QuotaRuleVo> rules) { this.rules = rules == null ? null : List.copyOf(rules); }
}
