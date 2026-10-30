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
public class TrialActivationVo implements EmbeddedDocument {
    @Field("trial_period_id")
    private String trialPeriodId;
    @Field("period_start_at")
    private Instant periodStartAt;
    @Field("period_end_at")
    private Instant periodEndAt;
    @Field("policy_snapshot_id")
    private String policySnapshotId;
}
