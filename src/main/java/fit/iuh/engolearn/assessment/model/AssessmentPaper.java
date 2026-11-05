package fit.iuh.engolearn.assessment.model;
import java.util.*;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;
import fit.iuh.engolearn.assessment.model.vo.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@lombok.experimental.SuperBuilder
@Document("assessment_papers")
@CompoundIndex(name="assessment_paper_version", def="{'series':1,'version':1}", unique=true)
public class AssessmentPaper extends fit.iuh.engolearn.shared.BaseDocument {
    @Field("series") private String series;
    @Field("version") private int version;
    @Field("rules_version") private int rulesVersion;
    @Field("published") private boolean published;
    @Field("demonstration") private boolean demonstration;
    @Field("skills") private List<AssessmentSkillVo> skills;
    @Field("units") private List<AssessmentUnitVo> units;
}
