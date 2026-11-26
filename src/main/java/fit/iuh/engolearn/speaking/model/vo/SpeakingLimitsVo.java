package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingLimitsVo implements EmbeddedDocument {
    @Field("max_turns") private int maxTurns;
    @Field("max_session_ms") private long maxSessionMs;
    @Field("max_turn_audio_ms") private long maxTurnAudioMs;
    @Field("closing_grace_ms") private long closingGraceMs;
}
