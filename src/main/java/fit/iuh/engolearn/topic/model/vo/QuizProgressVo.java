package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizProgressVo implements EmbeddedDocument {
    @Field("quiz_id")
    private String quizId;

    @Field("submissions")
    private List<QuizSubmissionVo> submissions;

    @Field("completed_at")
    private Instant completedAt;
}
