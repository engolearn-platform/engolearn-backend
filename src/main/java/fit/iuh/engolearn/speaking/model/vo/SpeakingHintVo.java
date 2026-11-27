package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingHintVo implements EmbeddedDocument {
    @Field("example_en") private String exampleEn;
    @Field("explanation_vi") private String explanationVi;
}
