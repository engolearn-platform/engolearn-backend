package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.enums.AttemptScope;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.AttemptType;
import fit.iuh.engolearn.topic.model.vo.AttemptItemSnapshotVo;
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
import java.util.List;

@Document("user_topic_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserTopicAttempt extends BaseDocument {
    @Field("user_id")
    private String userId;

    @Field(value = "topic_id", targetType = FieldType.OBJECT_ID)
    private String topicId;

    @Field("attempt_type")
    private AttemptType attemptType;

    @Field("scope")
    private AttemptScope scope;

    @Field(value = "topic_version_id", targetType = FieldType.OBJECT_ID)
    private String topicVersionId;

    @Field(value = "target_topic_item_id", targetType = FieldType.OBJECT_ID)
    private String targetTopicItemId;

    @Field("items_snapshot")
    private List<AttemptItemSnapshotVo> itemsSnapshot;

    @Field(value = "current_topic_item_id", targetType = FieldType.OBJECT_ID)
    private String currentTopicItemId;

    @Field("status")
    private AttemptStatus status;

    @Field("started_at")
    private Instant startedAt;

    @Field("last_accessed_at")
    private Instant lastAccessedAt;

    @Field("completed_at")
    private Instant completedAt;
}
