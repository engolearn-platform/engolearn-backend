package fit.iuh.engolearn.speaking.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpeakingStartVo implements EmbeddedDocument {
    @Field("session_id") private String sessionId;
    @Field("client_request_id") private String clientRequestId;
    @Field("source_session_id") private String sourceSessionId;
    @Field("source_epoch") private Long sourceEpoch;
    @Field("snapshot") private SpeakingSnapshotVo snapshot;
    @Field("limits") private SpeakingLimitsVo limits;
}
