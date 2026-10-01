package fit.iuh.engolearn.learning.progress.model;

import fit.iuh.engolearn.shared.model.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "user_topic_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserTopicProgress extends BaseDocument {
    @Field("user_id")
    private String userId;

    @Field("topic_id")
    private String topicId;

    @Field("topic_version_id")
    private String topicVersionId;

    @Field("last_topic_item_id")
    private String lastTopicItemId;

    @Field("is_completed")
    private Boolean isCompleted;

    @Field("completed_at")
    private Instant completedAt;

    @Field("last_accessed_at")
    private Instant lastAccessedAt;
}