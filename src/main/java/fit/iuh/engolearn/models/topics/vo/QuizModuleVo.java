package fit.iuh.engolearn.models.topics.vo;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import fit.iuh.engolearn.models.shared.enums.QuizType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizModuleVo implements EmbeddedDocument {
    @Field("quiz_id")
    private String quizId;

    @Field("quiz_type")
    private QuizType quizType;

    @Field("title")
    private String title;

    @Field("instructions")
    private String instructions;

    @Field("order_index")
    private Integer orderIndex;

    @Field("questions")
    private List<QuizQuestionVo> questions;
}
