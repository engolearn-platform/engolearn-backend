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
public class QuotaRuleVo implements EmbeddedDocument {
    @Field("quota_type")
    private String quotaType;
    @Field("display_name")
    private String displayName;
    @Field("limit")
    private long limit;
    @Field("unit")
    private String unit;
    @Field("period")
    private QuotaPeriod period;
    @Field("display_order")
    private int displayOrder;
}
