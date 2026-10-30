package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.enums.AttemptScope;
import fit.iuh.engolearn.topic.model.enums.RetryStatus;
import fit.iuh.engolearn.topic.model.vo.AttemptItemSnapshotVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document("topic_retry_runtime")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TopicRetryRuntime extends BaseDocument {
    @Field("generation")
    private long generation;

    @Field("user_id")
    private String userId;

    @Field("topic_id")
    private String topicId;

    @Version
    @Field("version")
    private Long version;

    @Field("session_id")
    private String sessionId;

    @Field("scope")
    private AttemptScope scope;

    @Field("status")
    private RetryStatus status;

    @Field("started_at")
    private Instant startedAt;

    @Field("completed_at")
    private Instant completedAt;

    @Field("items_snapshot")
    private List<AttemptItemSnapshotVo> itemsSnapshot;

    @Field("completed_item_ids")
    private List<String> completedItemIds;

    @Field("current_attempt")
    private UserTopicItemAttempt currentAttempt;

    @Field("official_results")
    private Map<String, UserTopicItemAttempt> officialResults;

    public String nextItemId() {
        return itemsSnapshot.stream().map(AttemptItemSnapshotVo::getTopicItemId)
                .filter(id -> !completedItemIds.contains(id)).findFirst().orElse(null);
    }
}
