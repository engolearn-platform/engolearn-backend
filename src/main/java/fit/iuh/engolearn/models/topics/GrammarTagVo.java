package fit.iuh.engolearn.models.topics;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrammarTagVo implements EmbeddedDocument {
    @Field("grammar_point_id")
    private String grammarPointId;

    @Field("contextual_explanation")
    private String contextualExplanation;

    @Field("tag_source")
    private String tagSource;
}