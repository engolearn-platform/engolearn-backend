package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document("topic_learner_state")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class TopicLearnerState extends BaseDocument {
    private String userId;
    private String topicId;
    private long generation;
    private List<ResetReceipt> receipts;
    public record ResetReceipt(String key, String fingerprint, long previousGeneration, long generation) {}
}
