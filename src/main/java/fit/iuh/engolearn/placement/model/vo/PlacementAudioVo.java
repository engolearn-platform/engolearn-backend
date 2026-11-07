package fit.iuh.engolearn.placement.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementAudioVo implements EmbeddedDocument {
    @Field("url") private String url;
    @Field("duration_seconds") private double durationSeconds;
}
