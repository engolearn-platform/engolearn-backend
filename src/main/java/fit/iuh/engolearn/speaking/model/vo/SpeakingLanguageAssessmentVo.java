package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingLanguageAssessmentVo implements EmbeddedDocument {
    @Field("grammar") private SpeakingCriterionVo grammar;
    @Field("vocabulary") private SpeakingCriterionVo vocabulary;
    @Field("feedback") private SpeakingAssessmentFeedbackVo feedback;
}
