package fit.iuh.engolearn.topic.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptItemSnapshotVo implements EmbeddedDocument {
    @Field(value = "topic_item_id", targetType = FieldType.OBJECT_ID)
    private String topicItemId;

    @Field(value = "topic_item_version_id", targetType = FieldType.OBJECT_ID)
    private String topicItemVersionId;

    @Field("order_index")
    private Integer orderIndex;
}
