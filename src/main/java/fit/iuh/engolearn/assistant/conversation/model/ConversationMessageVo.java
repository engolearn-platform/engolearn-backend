package fit.iuh.engolearn.assistant.conversation.model;

import fit.iuh.engolearn.assistant.conversation.model.ConversationRole;
import fit.iuh.engolearn.assistant.conversation.model.FeedbackType;
import fit.iuh.engolearn.shared.model.EmbeddedDocument;
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