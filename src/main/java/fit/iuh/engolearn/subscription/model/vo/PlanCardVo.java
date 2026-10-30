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
public class PlanCardVo implements EmbeddedDocument {
    @Field("plan_code")
    private PlanCode planCode;
    @Field("display_name")
    private String displayName;
    @Field("description")
    private String description;
    @Field("benefits")
    @Singular
    private List<PlanBenefitVo> benefits;
    @Field("billing_options")
    @Singular
    private List<PlanBillingOptionVo> billingOptions;

    public List<PlanBenefitVo> getBenefits() { return benefits == null ? null : List.copyOf(benefits); }
    public void setBenefits(List<PlanBenefitVo> benefits) { this.benefits = benefits == null ? null : List.copyOf(benefits); }

    public List<PlanBillingOptionVo> getBillingOptions() { return billingOptions == null ? null : List.copyOf(billingOptions); }
    public void setBillingOptions(List<PlanBillingOptionVo> billingOptions) { this.billingOptions = billingOptions == null ? null : List.copyOf(billingOptions); }
}
