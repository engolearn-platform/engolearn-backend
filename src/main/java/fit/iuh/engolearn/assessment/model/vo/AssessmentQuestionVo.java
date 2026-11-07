package fit.iuh.engolearn.assessment.model.vo;
import java.util.*;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;
import fit.iuh.engolearn.assessment.model.vo.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @EqualsAndHashCode
public class AssessmentQuestionVo implements fit.iuh.engolearn.shared.EmbeddedDocument {
    @Field("number") private int number;
    @Field("level") private String level;
    @Field("skill_code") private String skillCode;
    @Field("prompt") private String prompt;
    @Field("options") private List<String> options;
    @Field("correct_option") private int correctOption;
    @Field("explanation") private String explanation;
    @Field("transcript") private String transcript;
}
