package fit.iuh.engolearn.assessment.model.vo;
import java.util.*;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;
import fit.iuh.engolearn.assessment.model.vo.*;
import fit.iuh.engolearn.assessment.model.enums.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @EqualsAndHashCode
public class AssessmentSkillResultVo implements fit.iuh.engolearn.shared.EmbeddedDocument {
    @Field("code") private String code;
    @Field("name") private String name;
    @Field("example") private String example;
    @Field("section") private AssessmentSection section;
    @Field("correct") private int correct;
    @Field("total") private int total;
    @Field("percentage") private int percentage;
    @Field("band") private AssessmentSkillBand band;
}
