package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.EmbeddedDocument;
import fit.iuh.engolearn.content.topic.model.TopicItemPart;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FurthestReachVo implements EmbeddedDocument {
    @Field("stage")
    private TopicItemPart stage;

    @Field("step")
    private String step;
}
