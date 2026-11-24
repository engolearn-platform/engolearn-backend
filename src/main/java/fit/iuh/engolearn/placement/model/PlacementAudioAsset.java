package fit.iuh.engolearn.placement.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("placement_audio_assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class PlacementAudioAsset extends BaseDocument {
    @Field("status") private fit.iuh.engolearn.placement.model.enums.PlacementAudioAssetStatus status;
    @Field("media_id") private String mediaId;
    @Field("duration_seconds") private double durationSeconds;
    @Field("title") private String title;
}
