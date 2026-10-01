package fit.iuh.engolearn.assistant.conversation.model;

import fit.iuh.engolearn.assistant.conversation.model.ContextType;
import fit.iuh.engolearn.assistant.conversation.model.ConversationStatus;
import fit.iuh.engolearn.shared.model.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document("ai_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AiConversation extends BaseDocument {
    @Field("user_id")
    private String userId;

    @Field("context_type")
    private ContextType contextType;

    @Field("context_ref_id")
    private String contextRefId;

    @Field("context_snapshot")
    private ContextSnapshotVo contextSnapshot;

    @Field("messages")
    private List<ConversationMessageVo> messages;

    @Field("status")
    private ConversationStatus status;

    @Indexed(expireAfter = "0")
    @Field("expires_at")
    private Instant expiresAt;
}