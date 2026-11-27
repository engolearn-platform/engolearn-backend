package fit.iuh.engolearn.user.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.model.vo.UserAssessmentProgressVo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("user_profiles")
public class UserProfile extends BaseDocument {
    @Field("display_name")
    private String displayName;

    @Field("cefr_level")
    private CefrLevel cefrLevel;

    @Field("assessment_progress")
    private UserAssessmentProgressVo assessmentProgress;
}
