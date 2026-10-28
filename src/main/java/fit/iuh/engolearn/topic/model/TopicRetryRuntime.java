package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.vo.AttemptItemSnapshotVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document("topic_retry_runtime")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class TopicRetryRuntime extends BaseDocument {
    private String userId;
    private String topicId;
    @Version private Long version;
    private String sessionId;
    private String scope;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private List<AttemptItemSnapshotVo> itemsSnapshot;
    private List<String> completedItemIds;
    private UserTopicItemAttempt currentAttempt;
    private Map<String, UserTopicItemAttempt> officialResults;
}
