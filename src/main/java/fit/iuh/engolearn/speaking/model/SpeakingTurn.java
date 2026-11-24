package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("speaking_v2_turns")
@CompoundIndex(name = "speaking_v2_turn_sequence", def = "{'session_id':1,'value.sequence':1}", unique = true)
@CompoundIndex(name = "speaking_v2_client_turn", def = "{'session_id':1,'value.client_turn_id':1}", unique = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class SpeakingTurn extends BaseDocument {
    @Field("session_id") private String sessionId;
    @Field("value") private SpeakingTurnVo value;
}
