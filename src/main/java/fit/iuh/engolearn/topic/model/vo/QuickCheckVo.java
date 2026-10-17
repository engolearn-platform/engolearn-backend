package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickCheckVo implements EmbeddedDocument {
    @Field("sentence")
    private QuizSentenceVo sentence;

    @Field("quiz_data")
    private Map<String, Object> quizData;

    @Field("answer_data")
    private Map<String, Object> answerData;

    @Field("explanation")
    private String explanation;
}
