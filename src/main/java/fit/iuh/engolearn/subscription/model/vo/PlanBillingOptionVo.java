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
public class PlanBillingOptionVo implements EmbeddedDocument {
    @Field("billing_period")
    private BillingPeriod billingPeriod;
    @Field("amount")
    private long amount;
    @Field("currency")
    private String currency;
}
