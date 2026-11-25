package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioAssessmentStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingWordAssessmentVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document("speaking_v2_audio_assessments")
@CompoundIndex(name = "speaking_v2_audio_assessment_session", def = "{'session_id':1,'status':1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class SpeakingAudioAssessment extends BaseDocument {
    @Field("session_id") private String sessionId;
    @Field("sequence") private int sequence;
    @Field("provider") private String provider;
    @Field("locale") private String locale;
    @Field("policy_version") private String policyVersion;
    @Field("status") private SpeakingAudioAssessmentStatus status;
    @Field("token") private String token;
    @Field("worker_instance") private String workerInstance;
    @Field("claim_deadline") private Instant claimDeadline;
    @Field("attempt_count") private int attemptCount;
    @Field("finished_at") private Instant finishedAt;
    @Field("duration_ms") private long durationMs;
    @Field("assessed_speech_duration_ms") private Long assessedSpeechDurationMs;
    @Field("fluency_evidence_duration_ms") private Long fluencyEvidenceDurationMs;
    @Field("words") private int words;
    @Field("pronunciation") private Double pronunciation;
    @Field("pronunciation_reason") private String pronunciationReason;
    @Field("fluency") private Double fluency;
    @Field("fluency_reason") private String fluencyReason;
    @Field("word_feedback") private List<SpeakingWordAssessmentVo> wordFeedback;
    @Field("reason") private String reason;
}
