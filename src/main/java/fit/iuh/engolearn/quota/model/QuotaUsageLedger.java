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

@Document("quota_usage_ledger")
@CompoundIndexes({
    @CompoundIndex(name = "user_id_1_source_feature_1_idempotency_key_1", def = "{'user_id': 1, 'source_feature': 1, 'idempotency_key': 1}", unique = true),
    @CompoundIndex(name = "user_id_1_quota_type_1_recorded_at_1", def = "{'user_id': 1, 'quota_type': 1, 'recorded_at': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class QuotaUsageLedger extends BaseDocument {
    @Field("ledger_id")
    private String ledgerId;
    @Field("user_id")
    private String userId;
    @Field("quota_type")
    private String quotaType;
    @Field("amount")
    private Long amount;
    @Field("operation")
    private UsageOperation operation;
    @Field("source_feature")
    private String sourceFeature;
    @Field("idempotency_key")
    private String idempotencyKey;
    @Field("source_reference")
    private String sourceReference;
    @Field("occurred_at")
    private String occurredAt;
    @Field("recorded_at")
    private Instant recordedAt;
    @Field("policy_scope")
    private PolicyScope policyScope;
    @Field("policy_snapshot_id")
    private String policySnapshotId;
    @Field("policy_version")
    private Long policyVersion;
    @Field("allowance_id")
    private String allowanceId;
    @Field("allowance_start_at")
    private Instant allowanceStartAt;
    @Field("calendar_window_start_at")
    private Instant calendarWindowStartAt;
    @Field("calendar_window_end_at")
    private Instant calendarWindowEndAt;
    @Field("used_after")
    private Long usedAfter;
    @Field("limit_at_record")
    private Long limitAtRecord;
    @Field("unit")
    private String unit;
    @Field("period")
    private QuotaPeriod period;
}
