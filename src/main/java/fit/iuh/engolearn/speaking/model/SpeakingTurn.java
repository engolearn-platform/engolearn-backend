package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingChargeVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("speaking_v2_turns")
@CompoundIndex(name = "speaking_v2_turn_sequence", def = "{'session_id':1,'value.sequence':1}", unique = true)
@CompoundIndex(name = "speaking_v2_client_turn", def = "{'session_id':1,'value.client_turn_id':1}", unique = true)
@CompoundIndex(name = "speaking_v2_pending_charge", def = "{'quota_recorded':1,'updated_at':1}")
@CompoundIndex(name = "speaking_v2_owner_pending_charge", def = "{'owner_id':1,'quota_recorded':1,'value.sealed_at':1,'value.status':1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class SpeakingTurn extends BaseDocument {
    @Field("session_id") private String sessionId;
    @Field("owner_id") private String ownerId;
    @Field("charge") private SpeakingChargeVo charge;
    @Field("ledger_id") private String ledgerId;
    @Field("recorded_at") private java.time.Instant recordedAt;
    @Field("quota_recorded") private boolean quotaRecorded;
    @Field("value") private SpeakingTurnVo value;
}
