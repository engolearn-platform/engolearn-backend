package fit.iuh.engolearn.companion.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "companion_messages")
@CompoundIndex(name = "companion_conversation_sequence_unique", def = "{'conversation_id':1,'sequence':1}", unique = true)
@CompoundIndex(name = "companion_conversation_sequence_desc", def = "{'conversation_id':1,'sequence':-1}")
@CompoundIndex(name = "companion_conversation_turn_role_unique", def = "{'conversation_id':1,'turn_id':1,'role':1}", unique = true)
@Getter @Setter @NoArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class CompanionMessage extends BaseDocument {
    public enum Role { USER, ASSISTANT }

    @Field("conversation_id") private String conversationId;
    @Field("turn_id") private String turnId;
    @Field("sequence") private long sequence;
    @Field("role") private Role role;
    @Field("content") private String content;
}
