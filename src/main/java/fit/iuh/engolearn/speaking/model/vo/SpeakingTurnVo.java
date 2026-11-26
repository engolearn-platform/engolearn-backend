package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingTurnStatus;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingTurnVo implements EmbeddedDocument {
    @Field("turn_id") private String turnId;
    @Field("client_turn_id") private String clientTurnId;
    @Field("sequence") private int sequence;
    @Field("revision") private long revision;
    @Field("status") private SpeakingTurnStatus status;
    @Field("evidence_token") private String evidenceToken;
    @Field("work_deadline") private Instant workDeadline;
    @Field("delivery_token") private String deliveryToken;
    @Field("delivery_deadline") private Instant deliveryDeadline;
    @Field("successful_wait_ms") private long successfulWaitMs;
    @Field("delivered_audio_ms") private long deliveredAudioMs;
    @Field("work_token") private String workToken;
    @Field("attempt") private int attempt;
    @Field("transcript") private String transcript;
    @Field("reply") private String reply;
    @Field("hint") private SpeakingHintVo hint;
    @Field("correction") private SpeakingCorrectionVo correction;
    @Field("learner_audio") private SpeakingMediaVo learnerAudio;
    @Field("assistant_audio") private SpeakingMediaVo assistantAudio;
    @Field("audio_status") private SpeakingAudioStatus audioStatus;
    @Field("reply_delivered_at") private Instant replyDeliveredAt;
    @Field("speech_duration_ms") private long speechDurationMs;
    @Field("recording_started_at") private Instant recordingStartedAt;
    @Field("sealed_at") private Instant sealedAt;
    @Field("capture_complete") private Boolean captureComplete;
    @Field("memory_delta") private List<SpeakingMemoryVo.Delta> memoryDelta;
    @Field("reason") private String reason;
}
