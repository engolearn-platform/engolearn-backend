package fit.iuh.engolearn.placement.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import java.time.Instant;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementAnswerVo implements EmbeddedDocument {
    @Field("question_id") private String questionId;
    @Field("selected_option_id") private String selectedOptionId;
    @Field("answered_at") private Instant answeredAt;
}
