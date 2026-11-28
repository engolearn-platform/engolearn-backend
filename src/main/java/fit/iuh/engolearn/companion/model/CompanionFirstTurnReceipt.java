package fit.iuh.engolearn.companion.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "companion_first_turn_receipts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class CompanionFirstTurnReceipt extends BaseDocument {
    public enum State { ADMITTED, CANCELLED }

    @Field("learner_id") private String learnerId;
    @Field("turn_id") private String turnId;
    @Field("state") private State state;
    @Field("conversation_id") private String conversationId;
}
