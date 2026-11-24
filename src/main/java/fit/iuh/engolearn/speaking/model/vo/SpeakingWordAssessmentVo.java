package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingWordAssessmentVo implements EmbeddedDocument {
    @Field("word") private String word;
    @Field("accuracy_score") private Double accuracyScore;
    @Field("error_type") private String errorType;
}
