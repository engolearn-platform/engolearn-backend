package fit.iuh.engolearn.assessment.model;

import fit.iuh.engolearn.assessment.model.vo.AssessmentSkillVo;
import fit.iuh.engolearn.assessment.model.vo.AssessmentUnitVo;
import fit.iuh.engolearn.shared.BaseDocument;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@Document("assessment_paper_drafts")
public class AssessmentDraft extends BaseDocument {
    private String series;
    private String name;
    private int version;
    private String revision;
    @Field("import_token") private String importToken;
    @Field("previewed_by") private List<String> previewedBy;
    @Field("reviewed_revision") private String reviewedRevision;
    @Field("source_paper_id") private String sourcePaperId;
    private boolean demonstration;
    private List<AssessmentSkillVo> skills;
    private List<AssessmentUnitVo> units;
    // Ordinals into the immutable source paper; paths and hashes never come from clients.
    @Field("source_audio_units") private List<Integer> sourceAudioUnits;
}
