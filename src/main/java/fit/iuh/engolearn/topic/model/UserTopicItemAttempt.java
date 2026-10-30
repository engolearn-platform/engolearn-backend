package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.vo.FurthestReachedVo;
import fit.iuh.engolearn.topic.model.vo.QuizProgressVo;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

@Document("user_topic_item_attempts")
@CompoundIndex(
        name = "one_running_learning_item_per_topic",
        def = "{'user_id': 1, 'topic_id': 1, 'attempt_type': 1, 'generation': 1}",
        unique = true,
        partialFilter = "{'status': 'IN_PROGRESS', 'attempt_type': 'LEARNING'}"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserTopicItemAttempt extends BaseDocument {
    @Field("generation")
    private long generation;

    @Field("attempt_type")
    private fit.iuh.engolearn.topic.model.enums.AttemptType attemptType;

    @Field("start_request_key")
    private String startRequestKey;

    @Field("start_fingerprint")
    private String startFingerprint;

    @Field("mutation_receipts")
    private List<fit.iuh.engolearn.topic.model.vo.ContentMutationReceiptVo> mutationReceipts;

    @Field(value = "user_topic_attempt_id", targetType = FieldType.OBJECT_ID)
    private String userTopicAttemptId;

    @Field("user_id")
    private String userId;

    @Field(value = "topic_id", targetType = FieldType.OBJECT_ID)
    private String topicId;

    @Field(value = "topic_item_id", targetType = FieldType.OBJECT_ID)
    private String topicItemId;

    @Field(value = "topic_item_version_id", targetType = FieldType.OBJECT_ID)
    private String topicItemVersionId;

    @Field("status")
    private AttemptStatus status;

    @Field("furthest_reached")
    private FurthestReachedVo furthestReached;

    @Field("quiz_progress")
    private List<QuizProgressVo> quizProgress;

    @Field("started_at")
    private Instant startedAt;

    @Field("last_accessed_at")
    private Instant lastAccessedAt;

    @Field("completed_at")
    private Instant completedAt;

    @Field("completion_result")
    private fit.iuh.engolearn.topic.model.vo.CompletionResultVo completionResult;

    @Field("abandoned_reason")
    private String abandonedReason;

    @Version
    @Field("version")
    private Long version;
}
