package fit.iuh.engolearn.topic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicContextResponse {
    private String scenarioDescription;
    private String promptingQuestion;
    private List<TopicDialogueTurnResponse> dialogue;
    private Integer durationSec;
    private String tips;
    private String imageUrl;
    private String dialogueAudioUrl;
}
