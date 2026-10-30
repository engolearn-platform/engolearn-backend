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
public class CurrentBenefitsVo implements EmbeddedDocument {
    @Field("plan_code")
    private PlanCode planCode;
    @Field("display_name")
    private String displayName;
    @Field("policy_snapshot_id")
    private String policySnapshotId;
    @Field("benefits")
    @Singular
    private List<PlanBenefitVo> benefits;

    public List<PlanBenefitVo> getBenefits() { return benefits == null ? null : List.copyOf(benefits); }
    public void setBenefits(List<PlanBenefitVo> benefits) { this.benefits = benefits == null ? null : List.copyOf(benefits); }
}
