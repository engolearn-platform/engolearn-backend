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
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document("subscription_periods")
@CompoundIndexes({
    @CompoundIndex(name = "one_lifetime_trial", def = "{'user_id': 1}", unique = true,
        partialFilter = "{'kind': 'TRIAL'}"),
    @CompoundIndex(name = "user_id_1_period_start_at_-1", def = "{'user_id': 1, 'period_start_at': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SubscriptionPeriod extends BaseDocument {
    @Field("user_id")
    private String userId;
    @Field("kind")
    private PeriodKind kind;
    @Field("state")
    private PeriodState state;
    @Field("period_start_at")
    private Instant periodStartAt;
    @Field("period_end_at")
    private Instant periodEndAt;
    @Field("snapshot")
    private QuotaPolicySnapshotVo snapshot;
    @Field("benefits")
    private CurrentBenefitsVo benefits;
    @Field("prices")
    @Singular
    private List<PlanBillingOptionVo> prices;

    public List<PlanBillingOptionVo> getPrices() { return prices == null ? null : List.copyOf(prices); }
    public void setPrices(List<PlanBillingOptionVo> prices) { this.prices = prices == null ? null : List.copyOf(prices); }
}
