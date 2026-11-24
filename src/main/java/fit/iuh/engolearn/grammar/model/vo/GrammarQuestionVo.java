package fit.iuh.engolearn.grammar.model.vo;

import fit.iuh.engolearn.grammar.model.enums.GrammarQuestionType;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class GrammarQuestionVo implements EmbeddedDocument {
    @EqualsAndHashCode.Exclude
    @Field("question_id")
    private String id;
    @Field("type")
    private GrammarQuestionType type;
    @Field("prompt")
    private String prompt;
    @Field("explanation")
    private String explanation;
    @Field("options")
    private List<String> options;
    @Field("correct_index")
    private Integer correctIndex;
    @Field("correct_answer")
    private String correctAnswer;
    @Field("accepted_answers")
    private List<String> acceptedAnswers;
}
