package fit.iuh.engolearn.models.learning_assistant;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextSnapshotVo implements EmbeddedDocument {
    @Field("word")
    private String word;

    @Field("sentence")
    private String sentence;

    @Field("explanation")
    private String explanation;
}