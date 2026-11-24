package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingAssessmentVo implements EmbeddedDocument {
    @Field("pronunciation") private SpeakingCriterionVo pronunciation;
    @Field("fluency") private SpeakingCriterionVo fluency;
    @Field("grammar") private SpeakingCriterionVo grammar;
    @Field("vocabulary") private SpeakingCriterionVo vocabulary;
    @Field("feedback") private SpeakingAssessmentFeedbackVo feedback;
    @Field("acoustic_policy_version") private String acousticPolicyVersion;
    @Field("language_rubric_version") private String languageRubricVersion;
    @Field("finalized_at") private Instant finalizedAt;
}
