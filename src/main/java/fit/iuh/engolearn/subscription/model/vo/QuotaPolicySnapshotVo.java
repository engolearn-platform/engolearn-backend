package fit.iuh.engolearn.subscription.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.subscription.model.enums.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode
public class QuotaPolicySnapshotVo implements EmbeddedDocument {
    @Field("policy_snapshot_id")
    private String policySnapshotId;
    @Field("policy_scope")
    private PolicyScope policyScope;
    @Field("policy_version")
    private long policyVersion;
    @Field("rules")
    @Singular
    private List<QuotaRuleVo> rules;

    public List<QuotaRuleVo> getRules() { return rules == null ? null : List.copyOf(rules); }
    public void setRules(List<QuotaRuleVo> rules) { this.rules = rules == null ? null : List.copyOf(rules); }
}
