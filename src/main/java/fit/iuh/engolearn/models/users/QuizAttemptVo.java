package fit.iuh.engolearn.models.users;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttemptVo implements EmbeddedDocument {
    @Field("quiz_id")
    private String quizId;

    @Field("wrong_count")
    private Integer wrongCount;

    @Field("wrong_answers_log")
    private List<String> wrongAnswersLog;
}
