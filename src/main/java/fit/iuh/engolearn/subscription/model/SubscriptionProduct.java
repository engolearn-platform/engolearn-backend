package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.subscription.model.enums.ProductSaleStatus;
import fit.iuh.engolearn.subscription.model.enums.ProductVisibility;
import fit.iuh.engolearn.subscription.model.vo.ProductContentVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("subscription_products")
@CompoundIndex(name = "product_code_unique", def = "{'plan_code':1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class SubscriptionProduct extends BaseDocument {
    @Field("plan_code") private String planCode;
    @Field("visibility") private ProductVisibility visibility;
    @Field("sale_status") private ProductSaleStatus saleStatus;
    @Field("archived") private boolean archived;
    @Field("current_version") private Long currentVersion;
    @Field("draft_revision") private long draftRevision;
    @Field("draft_content") private ProductContentVo draftContent;
    @Field("purchase_fence") private long purchaseFence;
    @Field("legacy_schedule_version") private Long legacyScheduleVersion;
}
