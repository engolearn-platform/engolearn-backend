package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("topic_retry_archive")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class TopicRetryArchive extends BaseDocument {
    private long generation;
    private String userId;
    private String topicId;
    private String sessionId;
    private Long sourceVersion;
    private UserTopicItemAttempt attempt;
}
