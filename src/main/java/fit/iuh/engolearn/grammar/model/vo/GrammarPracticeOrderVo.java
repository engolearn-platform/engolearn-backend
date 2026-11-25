package fit.iuh.engolearn.grammar.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
public class GrammarPracticeOrderVo implements EmbeddedDocument {
    @Field("question_index")
    private int questionIndex;
    @Field("option_order")
    private List<Integer> optionOrder;
}
