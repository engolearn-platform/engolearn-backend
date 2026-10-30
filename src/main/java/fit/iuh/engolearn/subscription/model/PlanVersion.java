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

@Document("subscription_plan_versions")
@CompoundIndex(name = "scope_1_version_1", def = "{'scope': 1, 'version': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PlanVersion extends BaseDocument {
    @Field("scope")
    private PolicyScope scope;
    @Field("version")
    private long version;
    @Field("effective_at")
    private Instant effectiveAt;
    @Field("published")
    private boolean published;
    @Field("display_order")
    private int displayOrder;
    @Field("plan")
    private PlanCardVo plan;
}
