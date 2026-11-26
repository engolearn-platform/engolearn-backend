package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.speaking.model.enums.*;
import fit.iuh.engolearn.speaking.model.vo.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document("speaking_v2_sessions")
@CompoundIndex(name = "speaking_v2_owner_request", def = "{'owner_id':1,'client_request_id':1}", unique = true)
@CompoundIndex(name = "speaking_v2_unfinished_owner", def = "{'unfinished_owner':1}", unique = true,
        partialFilter = "{'unfinished_owner':{'$type':'string'}}")
@CompoundIndex(name = "speaking_v2_history", def = "{'owner_id':1,'deleted_at':1,'created_at':-1,'_id':-1}")
@CompoundIndex(name = "speaking_v2_parent_history", def = "{'owner_id':1,'deleted_at':1,'snapshot.topic_id':1,'created_at':-1,'_id':-1}")
@CompoundIndex(name = "speaking_v2_status", def = "{'status':1,'created_at':1}")
@CompoundIndex(name = "speaking_v2_assessment_work", def = "{'assessment_status':1,'deleted_at':1,'_id':1}")
@CompoundIndex(name = "speaking_v2_scope", def = "{'owner_id':1,'snapshot.scope_type':1,'snapshot.topic_id':1,'snapshot.topic_item_id':1,'created_at':-1,'_id':-1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class SpeakingSession extends BaseDocument {
    @Version @Field("revision") private Long revision;
    @Field("owner_id") private String ownerId;
    @Field("unfinished_owner") private String unfinishedOwner;
    @Field("client_request_id") private String clientRequestId;
    @Field("source_session_id") private String sourceSessionId;
    @Field("source_epoch") private Long sourceEpoch;
    @Field("replacement") private SpeakingStartVo replacement;
    @Field("snapshot") private SpeakingSnapshotVo snapshot;
    @Field("limits") private SpeakingLimitsVo limits;
    @Field("status") private SpeakingSessionStatus status;
    @Field("end_reason") private SpeakingEndReason endReason;
    @Field("started_at") private Instant startedAt;
    @Field("closing_cutoff") private Instant closingCutoff;
    @Field("ended_at") private Instant endedAt;
    @Field("deleted_at") private Instant deletedAt;
    @Field("controller_epoch") private long controllerEpoch;
    @Field("resume_request_id") private String resumeRequestId;
    @Field("ticket_hash") private String ticketHash;
    @Field("ticket_expires_at") private Instant ticketExpiresAt;
    @Field("lease_id") private String leaseId;
    @Field("lease_instance") private String leaseInstance;
    @Field("prompt_version") private String promptVersion;
    @Field("opening_token") private String openingToken;
    @Field("opening_deadline") private Instant openingDeadline;
    @Field("memory") private SpeakingMemoryVo memory;
    @Field("opening") private SpeakingOpeningVo opening;
    @Field("retry_closed_through_sequence") private int retryClosedThroughSequence;
    @Field("last_turn_sequence") private int lastTurnSequence;
    @Field("finalized_turn_count") private int finalizedTurnCount;
    @Field("committed_turn_count") private int committedTurnCount;
    @Field("speech_duration_ms") private long speechDurationMs;
    @Field("active_turn") private SpeakingTurnVo activeTurn;
    @Field("assessment_status") private SpeakingAssessmentStatus assessmentStatus;
    @Field("assessment_audio_deadline") private Instant assessmentAudioDeadline;
    @Field("assessment_language_deadline") private Instant assessmentLanguageDeadline;
    @Field("language_assessment") private SpeakingLanguageAssessmentVo languageAssessment;
    @Field("assessment") private SpeakingAssessmentVo assessment;
}
