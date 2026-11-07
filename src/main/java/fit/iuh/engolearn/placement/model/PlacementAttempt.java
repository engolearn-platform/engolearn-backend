package fit.iuh.engolearn.placement.model;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.vo.PlacementAnswerVo;
import fit.iuh.engolearn.shared.BaseDocument;
import java.time.Instant;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("placement_attempts")
@CompoundIndexes({
        @CompoundIndex(name = "one_active_guest_attempt", def = "{'guest_session_id': 1}", unique = true,
                partialFilter = "{'user_id': null, 'status': 'IN_PROGRESS'}"),
        @CompoundIndex(name = "one_blocking_user_attempt", def = "{'user_id': 1}", unique = true,
                partialFilter = "{'user_id': {'$type': 'string'}, "
                        + "'status': {'$in': ['IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED']}}"),
        @CompoundIndex(name = "one_reserved_start_session", def = "{'start_session_key': 1}", unique = true,
                partialFilter = "{'start_session_key': {'$type': 'string'}}"),
        @CompoundIndex(name = "placement_attempt_user_started", def = "{'user_id': 1, 'started_at': -1}"),
        @CompoundIndex(name = "placement_attempt_guest_started",
                def = "{'guest_session_id': 1, 'user_id': 1, 'started_at': -1}")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PlacementAttempt extends BaseDocument {
    @Field("user_id") private String userId;
    @Field("guest_session_id") private String guestSessionId;
    @Field("start_session_key") private String startSessionKey;
    @Field("test_set_id") private String testSetId;
    @Field("test_set_version") private int testSetVersion;
    @Field("status") private PlacementAttemptStatus status;
    @Field("started_at") private Instant startedAt;
    @Field("deadline_at") private Instant deadlineAt;
    @Field("ended_at") private Instant endedAt;
    @Field("claim_expires_at") private Instant claimExpiresAt;
    @Field("answers") private List<PlacementAnswerVo> answers;
    @Field("correct_count") private Integer correctCount;
    @Field("placement_level") private PlacementLevel placementLevel;
    @Field("revision") private long revision;
}
