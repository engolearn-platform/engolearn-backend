package fit.iuh.engolearn.grammar.model.vo;

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
public class GrammarPracticeAnswerVo implements EmbeddedDocument {
    @Field("request_id")
    private String requestId;
    @Field("correct")
    private boolean correct;
    @Field("option_index")
    private Integer optionIndex;
    @Field("answer_preview")
    private String answerPreview;
}
