package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import fit.iuh.engolearn.topic.model.vo.AttemptItemSnapshotVo;
import java.time.Instant;
import java.util.List;

@Document("topic_retry_locators")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class TopicRetryLocator extends BaseDocument {
    private long generation;
    private String startRequestKey;
    private String userId;
    private String topicId;
    private String sessionId;
    private String kind;
    private String fingerprint;
    private UserTopicItemAttempt initialAttempt;
    private String scope;
    private List<AttemptItemSnapshotVo> itemsSnapshot;
    private Instant startedAt;
    private String initialAttemptId;
}
