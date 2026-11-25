package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingRubricExampleVo implements EmbeddedDocument {
    @Field("turn_id") private String turnId;
    @Field("original") private String original;
    @Field("suggested") private String suggested;
}
