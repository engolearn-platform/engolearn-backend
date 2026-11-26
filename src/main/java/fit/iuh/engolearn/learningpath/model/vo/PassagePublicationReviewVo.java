package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.learningpath.model.enums.PassageReviewCheck;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassagePublicationReviewVo implements EmbeddedDocument {
    @Field("source_revision")
    private int sourceRevision;
    @Field("confirmed_checks")
    private List<PassageReviewCheck> confirmedChecks;
    @Field("reviewed_by")
    private String reviewedBy;
    @Field("reviewed_at")
    private Instant reviewedAt;
}
