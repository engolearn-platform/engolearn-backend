package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingAssessmentFeedbackVo implements EmbeddedDocument {
    @Field("strengths_vi") private List<String> strengthsVi;
    @Field("improvements_vi") private List<String> improvementsVi;
    @Field("recommendations_vi") private List<String> recommendationsVi;
    @Field("words_to_practice") private List<SpeakingWordAssessmentVo> wordsToPractice;
}
