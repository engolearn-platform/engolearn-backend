package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.enums.AttemptScope;
import fit.iuh.engolearn.topic.model.enums.RetryLocatorKind;
import fit.iuh.engolearn.topic.model.vo.AttemptItemSnapshotVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document("topic_retry_locators")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TopicRetryLocator extends BaseDocument {
    @Field("generation")
    private long generation;

    @Field("start_request_key")
    private String startRequestKey;

    @Field("user_id")
    private String userId;

    @Field("topic_id")
    private String topicId;

    @Field("session_id")
    private String sessionId;

    @Field("kind")
    private RetryLocatorKind kind;

    @Field("fingerprint")
    private String fingerprint;

    @Field("initial_attempt")
    private UserTopicItemAttempt initialAttempt;

    @Field("scope")
    private AttemptScope scope;

    @Field("items_snapshot")
    private List<AttemptItemSnapshotVo> itemsSnapshot;

    @Field("started_at")
    private Instant startedAt;

    @Field("initial_attempt_id")
    private String initialAttemptId;
}
