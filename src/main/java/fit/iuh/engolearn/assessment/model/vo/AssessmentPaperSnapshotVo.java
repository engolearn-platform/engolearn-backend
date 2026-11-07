package fit.iuh.engolearn.assessment.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @EqualsAndHashCode
public class AssessmentPaperSnapshotVo implements EmbeddedDocument {
    @Field("paper_id") private String paperId;
    @Field("series") private String series;
    @Field("version") private int version;
    @Field("rules_version") private int rulesVersion;
    @Field("demonstration") private boolean demonstration;
    @Field("skills") private List<AssessmentSkillVo> skills;
    @Field("units") private List<AssessmentUnitVo> units;
}
