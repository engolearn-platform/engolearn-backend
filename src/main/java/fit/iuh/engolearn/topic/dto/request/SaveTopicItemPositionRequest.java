package fit.iuh.engolearn.topic.dto.request;

import fit.iuh.engolearn.topic.model.enums.LearningStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveTopicItemPositionRequest {
    private String topicItemId;
    private LearningStage stage;
    private Integer itemIndex;
}
