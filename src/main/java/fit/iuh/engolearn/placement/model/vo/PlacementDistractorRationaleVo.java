package fit.iuh.engolearn.placement.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementDistractorRationaleVo implements EmbeddedDocument {
    @Field("option_id") private String optionId;
    @Field("reason") private String reason;
}
