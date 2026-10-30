package fit.iuh.engolearn.quota.model.vo;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.quota.model.enums.QuotaPeriod;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class QuotaRuleVo implements EmbeddedDocument {
    @Field("quota_type")
    private String quotaType;
    @Field("display_name")
    private String displayName;
    @Field("limit")
    private Long limit;
    @Field("unit")
    private String unit;
    @Field("period")
    private QuotaPeriod period;
    @Field("display_order")
    private Integer displayOrder;
}
