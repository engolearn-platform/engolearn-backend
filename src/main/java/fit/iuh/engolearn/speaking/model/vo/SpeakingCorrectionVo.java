package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.speaking.model.enums.SpeakingCorrectionType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingCorrectionVo implements EmbeddedDocument {
    @Field("type") private SpeakingCorrectionType type;
    @Field("original") private String original;
    @Field("suggested") private String suggested;
    @Field("explanation_vi") private String explanationVi;
}
