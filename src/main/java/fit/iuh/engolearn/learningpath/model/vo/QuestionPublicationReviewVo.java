package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionPublicationReviewVo implements EmbeddedDocument {
    @Field("source_revision")
    private Integer sourceRevision;
    @Field("passage_id")
    private String passageId;
    @Field("passage_revision")
    private Integer passageRevision;
    @Field("confirmed_checks")
    private List<QuestionReviewCheck> confirmedChecks;
    @Field("reviewed_by")
    private String reviewedBy;
    @Field("reviewed_at")
    private Instant reviewedAt;
}
