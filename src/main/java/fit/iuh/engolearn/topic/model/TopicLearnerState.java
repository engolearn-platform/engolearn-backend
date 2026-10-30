package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.vo.ResetReceiptVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document("topic_learner_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TopicLearnerState extends BaseDocument {
    @Field("user_id")
    private String userId;

    @Field("topic_id")
    private String topicId;

    @Field("generation")
    private long generation;

    @Field("receipts")
    private List<ResetReceiptVo> receipts;
}
