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
public class PlanBenefitVo implements EmbeddedDocument {
    @Field("code")
    private String code;
    @Field("title")
    private String title;
    @Field("description")
    private String description;
    @Field("display_value")
    private String displayValue;
    @Field("sort_order")
    private int sortOrder;
}
