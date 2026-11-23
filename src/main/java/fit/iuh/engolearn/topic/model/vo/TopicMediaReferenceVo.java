package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicMediaReferenceVo implements EmbeddedDocument {
    @Field("media_id")
    private String mediaId;

    @Field("kind")
    private TopicMediaKind kind;
}
