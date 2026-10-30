package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
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

@Document("user_topic_item_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserTopicItemProgress extends BaseDocument {
    @Field("generation")
    private long generation;

    @Field("user_id")
    private String userId;

    @Field(value = "topic_id", targetType = FieldType.OBJECT_ID)
    private String topicId;

    @Field(value = "topic_item_id", targetType = FieldType.OBJECT_ID)
    private String topicItemId;

    @Field("status")
    private ItemProgressStatus status;

    @Field(value = "last_completed_attempt_id", targetType = FieldType.OBJECT_ID)
    private String lastCompletedAttemptId;

    @Field(value = "completed_topic_item_version_id", targetType = FieldType.OBJECT_ID)
    private String completedTopicItemVersionId;

    @Field("first_try_correct_count")
    private Integer firstTryCorrectCount;

    @Field("total_quiz_count")
    private Integer totalQuizCount;

    @Field("completed_at")
    private Instant completedAt;

    @Field("last_accessed_at")
    private Instant lastAccessedAt;
}
