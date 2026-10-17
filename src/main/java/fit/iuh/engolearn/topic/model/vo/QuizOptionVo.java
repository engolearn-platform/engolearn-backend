package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizOptionVo implements EmbeddedDocument {
    @Field("label")
    private String label;

    @Field("text_en")
    private String textEn;

    @Field("is_correct")
    private Boolean isCorrect;
}
