package fit.iuh.engolearn.learning.progress.model;

import fit.iuh.engolearn.shared.model.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "user_topic_item_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserTopicItemProgress extends BaseDocument {
    @Field("user_id")
    private String userId;

    @Field("topic_item_id")
    private String topicItemId;

    @Field("topic_item_version")
    private Integer topicItemVersion;

    @Field("is_outdated")
    private Boolean isOutdated;

    @Field("status")
    private ItemProgressStatus status;

    @Field("furthest_reach")
    private ProgressReach furthestReach;

    @Field("quiz_attempts")
    private List<QuizAttemptVo> quizAttempts;

    @Field("speaking_score")
    private Double speakingScore;

    @Field("completed_at")
    private Instant completedAt;
}
