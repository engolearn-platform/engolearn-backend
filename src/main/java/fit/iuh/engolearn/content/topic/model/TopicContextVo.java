package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicContextVo implements EmbeddedDocument {
    @Field("prompt")
    private String prompt;

    @Field("scenario_description")
    private String scenarioDescription;

    @Field("dialogue")
    private List<DialogueTurnVo> dialogue;

    @Field("duration_sec")
    private Integer durationSec;

    @Field("tips")
    private String tips;
}
