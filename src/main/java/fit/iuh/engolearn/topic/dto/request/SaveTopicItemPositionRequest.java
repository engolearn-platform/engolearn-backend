package fit.iuh.engolearn.topic.dto.request;

import fit.iuh.engolearn.topic.model.enums.LearningStage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveTopicItemPositionRequest {
    @NotBlank
    private String topicItemId;
    @NotNull
    private LearningStage stage;
    @Min(0)
    private Integer itemIndex;
}
