package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioStatus;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingOpeningVo implements EmbeddedDocument {
    @Field("reply") private String reply;
    @Field("hint") private SpeakingHintVo hint;
    @Field("audio_status") private SpeakingAudioStatus audioStatus;
    @Field("audio") private SpeakingMediaVo audio;
}
