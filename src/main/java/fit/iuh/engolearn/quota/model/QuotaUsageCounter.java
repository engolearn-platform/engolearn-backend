package fit.iuh.engolearn.quota.model;
import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.quota.model.enums.*;
import fit.iuh.engolearn.quota.model.vo.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document("quota_usage_counters")
@CompoundIndexes({
    @CompoundIndex(name = "user_id_1_quota_type_1", def = "{'user_id': 1, 'quota_type': 1}", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class QuotaUsageCounter extends BaseDocument {
    @Field("user_id")
    private String userId;
    @Field("quota_type")
    private String quotaType;
    @Field("used_amount")
    private Long usedAmount;
    @Field("unit")
    private String unit;
    @Field("period")
    private QuotaPeriod period;
    @Field("calendar_window_start_at")
    private Instant calendarWindowStartAt;
    @Field("calendar_window_end_at")
    private Instant calendarWindowEndAt;
    @Field("allowance_start_at")
    private Instant allowanceStartAt;
    @Field("allowance_id")
    private String allowanceId;
    @Field("revision")
    private Long revision;
    @Field("last_applied_activation_sequence")
    private Long lastAppliedActivationSequence;
    @Field("last_applied_policy_activation_id")
    private String lastAppliedPolicyActivationId;
}
