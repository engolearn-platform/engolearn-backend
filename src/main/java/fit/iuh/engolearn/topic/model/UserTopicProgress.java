package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Document("user_topic_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserTopicProgress extends BaseDocument {
    @Field("user_id")
    private String userId;

    @Field(value = "topic_id", targetType = FieldType.OBJECT_ID)
    private String topicId;

    @Field(value = "last_topic_item_id", targetType = FieldType.OBJECT_ID)
    private String lastTopicItemId;

    @Field("last_completed_at")
    private Instant lastCompletedAt;

    @Field("last_accessed_at")
    private Instant lastAccessedAt;
}
