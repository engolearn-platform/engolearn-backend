package fit.iuh.engolearn.companion.model;

import fit.iuh.engolearn.shared.BaseDocument;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document(collection = "companion_conversations")
@CompoundIndex(name = "companion_owner_updated", def = "{'learner_id':1,'updated_at':-1,'_id':-1}")
@Getter @Setter @NoArgsConstructor @SuperBuilder @EqualsAndHashCode(callSuper = true)
public class CompanionConversation extends BaseDocument {
    @Field("learner_id") private String learnerId;
    @Field("title") private String title;
    @Field("message_count") private int messageCount;
    @Field("next_sequence") private long nextSequence;
    @Field("active_turn_id") private String activeTurnId;
    @Field("active_turn_generation") private long activeTurnGeneration;
    @Field("active_turn_lease_until") private Instant activeTurnLeaseUntil;
    @Field("pending_turn_id") private String pendingTurnId;
    @Field("pending_user_sequence") private Long pendingUserSequence;
}
