package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingMediaVo implements EmbeddedDocument {
    @Field("media_id") private String mediaId;
    @Field("stored_at") private Instant storedAt;
}
