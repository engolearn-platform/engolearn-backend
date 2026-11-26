package fit.iuh.engolearn.placement.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementOptionVo implements EmbeddedDocument {
    @Field("id") private String id;
    @Field("label") private String label;
    @Field("text") private String text;
}
