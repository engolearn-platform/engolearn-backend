package fit.iuh.engolearn.assessment.model.vo;
import java.util.*;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;
import fit.iuh.engolearn.assessment.model.vo.*;
import fit.iuh.engolearn.assessment.model.enums.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @EqualsAndHashCode
public class AssessmentUnitVo implements fit.iuh.engolearn.shared.EmbeddedDocument {
    @Field("kind") private AssessmentUnitKind kind;
    @Field("passage") private String passage;
    @Field("audio_path") private String audioPath;
    @Field("audio_sha256") private String audioSha256;
    @Field("questions") private List<AssessmentQuestionVo> questions;
}
