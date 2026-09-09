package fit.iuh.engolearn.models.topics;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import fit.iuh.engolearn.models.shared.enums.QuizType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizItemVo implements EmbeddedDocument {
    @Field("quiz_id")
    private String quizId;

    @Field("quiz_type")
    private QuizType quizType;

    @Field("order_index")
    private Integer orderIndex;

    @Field("question_text")
    private String questionText;

    @Field("sentence")
    private QuizSentenceVo sentence;

    @Field("quiz_data")
    private Map<String, Object> quizData;

    @Field("answer_data")
    private Map<String, Object> answerData;

    @Field("explanation")
    private String explanation;

    @Field("grammar_tags")
    private List<GrammarTagVo> grammarTags;
}