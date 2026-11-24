package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.subscription.model.vo.ProductContentVo;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("subscription_product_versions")
@CompoundIndex(name = "product_version_unique", def = "{'plan_code':1,'version':1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class SubscriptionProductVersion extends BaseDocument {
    @Field("plan_code") private String planCode;
    @Field("version") private long version;
    @Field("effective_at") private Instant effectiveAt;
    @Field("managed") private boolean managed;
    @Field("content") private ProductContentVo content;
    @Field("legacy_schedule_status") private String legacyScheduleStatus;
}
