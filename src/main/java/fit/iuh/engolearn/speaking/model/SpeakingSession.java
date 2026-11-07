package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document("speaking_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SpeakingSession extends BaseDocument {
    public enum Status { ACTIVE, INTERRUPTED, COMPLETED, INCOMPLETE }
    public enum EndReason {
        GOALS_REACHED, LEARNER_ENDED, SESSION_AGE, TURN_LIMIT, DAILY_QUOTA,
        RECOVERY_EXPIRED, EVALUATION_FAILURES, SERVICE_RESTART, REPLACED, SOFT_DELETED
    }

    @Field("learner_id") private String learnerId;
    @Field("open_learner_id") private String openLearnerId;
    @Field("content") private SpeakingContentSnapshot content;
    @Field("status") private Status status;
    @Field("end_reason") private EndReason endReason;
    @Field("started_at") private Instant startedAt;
    @Field("expires_at") private Instant expiresAt;
    @Field("ended_at") private Instant endedAt;
    @Field("deleted_at") private Instant deletedAt;
    @Field("disconnected_at") private Instant disconnectedAt;
    @Field("closing_at") private Instant closingAt;
    @Field("closing_reason") private EndReason closingReason;
    @Field("controller_epoch") private long controllerEpoch;
    @Version private Long revision;
    @Field("committed_turn_count") private int committedTurnCount;
    @Field("valid_turn_count") private int validTurnCount;
    @Field("consecutive_evaluation_failures") private int consecutiveEvaluationFailures;
    @Field("achieved_goal_ids") private List<String> achievedGoalIds;
    @Field("opening") private SpeakingOpening opening;
    @Field("last_resume_request_id") private String lastResumeRequestId;
    @Field("end_request_id") private String endRequestId;

    public boolean isOpen() {
        return status == Status.ACTIVE || status == Status.INTERRUPTED;
    }
}
