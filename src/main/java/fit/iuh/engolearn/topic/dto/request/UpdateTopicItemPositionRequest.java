package fit.iuh.engolearn.topic.dto.request;

import fit.iuh.engolearn.topic.model.enums.LearningStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTopicItemPositionRequest {
    private LearningStage stage;
    private Integer itemIndex;
}
