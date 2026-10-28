package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document("speaking_turns")
@CompoundIndex(name = "session_sequence_unique", def = "{'session_id': 1, 'sequence': 1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SpeakingTurn extends BaseDocument {
    public enum EvaluationState { PENDING, INVALID, SUCCEEDED, FAILED }

    @Field("session_id") private String sessionId;
    @Field("sequence") private int sequence;
    @Field("transcript") private String transcript;
    @Field("committed_at") private Instant committedAt;
    @Field("valid") private boolean valid;
    @Field("evaluation_state") private EvaluationState evaluationState;
    @Field("ai_reply") private String aiReply;
}
