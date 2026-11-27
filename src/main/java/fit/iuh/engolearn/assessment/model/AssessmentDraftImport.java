package fit.iuh.engolearn.assessment.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@NoArgsConstructor
@SuperBuilder
@Document("assessment_draft_imports")
public class AssessmentDraftImport extends BaseDocument {
    private String series;
    @Field("sound_check_sha256") private String soundCheckSha256;
}
