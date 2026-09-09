package fit.iuh.engolearn.models.learning_assistant;

import fit.iuh.engolearn.models.learning_assistant.enums.ConversationRole;
import fit.iuh.engolearn.models.learning_assistant.enums.FeedbackType;
import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMessageVo implements EmbeddedDocument {
    @Field("role")
    private ConversationRole role;

    @Field("content")
    private String content;

    @Field("created_at")
    private Instant createdAt;

    @Field("feedback")
    private FeedbackType feedback;
}