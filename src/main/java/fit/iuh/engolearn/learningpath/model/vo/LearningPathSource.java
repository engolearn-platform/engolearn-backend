package fit.iuh.engolearn.learningpath.model.vo;

import java.util.Date;
import fit.iuh.engolearn.shared.EmbeddedDocument;
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
public class LearningPathSource implements EmbeddedDocument {
    @Field("assessment_attempt_id")
    private String assessmentAttemptId;
    @Field("paper_id")
    private String paperId;
    @Field("taken_at")
    private Date takenAt;
}
