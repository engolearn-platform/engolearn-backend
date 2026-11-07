package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document("speaking_audio_assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SpeakingAudioAsset extends BaseDocument {
    public enum Kind { LEARNER_RECORDING, AI_REPLY, AI_OPENING }

    @Field("session_id") private String sessionId;
    @Field("turn_id") private String turnId;
    @Field("kind") private Kind kind;
    @Field("cloudinary_public_id") private String cloudinaryPublicId;
    @Field("format") private String format;
    @Field("content_type") private String contentType;
    @Field("created_at_audio") private Instant recordedAt;
    @Field("playback_until") private Instant playbackUntil;
}
