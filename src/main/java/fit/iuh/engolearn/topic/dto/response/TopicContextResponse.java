package fit.iuh.engolearn.topic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicContextResponse {
    private String scenarioDescription;
    private String promptingQuestion;
    private List<TopicDialogueTurnResponse> dialogue;
    private Integer durationSec;
    private String tips;
}
