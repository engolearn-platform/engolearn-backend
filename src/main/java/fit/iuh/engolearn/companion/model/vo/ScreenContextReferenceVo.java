package fit.iuh.engolearn.companion.model.vo;

import fit.iuh.engolearn.companion.model.enums.ScreenContextType;
import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScreenContextReferenceVo implements EmbeddedDocument {
    @Field("type") private ScreenContextType type;
    @Field("topic_id") private String topicId;
    @Field("attempt_id") private String attemptId;
    @Field("stage") private String stage;
    @Field("item_index") private Integer itemIndex;
}
