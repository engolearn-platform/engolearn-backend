package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestionVo implements EmbeddedDocument {
    @Field("prompt")
    private String prompt;

    @Field("audio_url")
    private String audioUrl;

    @Field("options")
    private List<QuizOptionVo> options;

    @Field("correct")
    private String correct;

    @Field("explanation_ok")
    private String explanationOk;

    @Field("explanation_ng")
    private String explanationNg;

    @Field("sentence")
    private QuizSentenceVo sentence;

    @Field("quiz_data")
    private Map<String, Object> quizData;

    @Field("answer_data")
    private Map<String, Object> answerData;

    @Field("explanation")
    private String explanation;

    @Field("tips")
    private String tips;
}
