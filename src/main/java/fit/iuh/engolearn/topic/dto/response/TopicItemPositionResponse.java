package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicItemPositionResponse {
    private String topicItemId;
    private String topicItemVersionId;
    private ItemProgressStatus status;
    private LearningStage stage;
    private Integer itemIndex;
    private Instant lastAccessedAt;
}
