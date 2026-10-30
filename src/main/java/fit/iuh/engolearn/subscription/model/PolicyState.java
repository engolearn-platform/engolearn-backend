package fit.iuh.engolearn.subscription.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.subscription.model.enums.*;
import fit.iuh.engolearn.subscription.model.vo.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("subscription_policy_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PolicyState extends BaseDocument {
    @Field("active_version_id")
    private String activeVersionId;
    @Field("pending_version_id")
    private String pendingVersionId;
    @Field("pending_effective_at")
    private Instant pendingEffectiveAt;
    @Field("activation_sequence")
    private long activationSequence;
    @Field("revision")
    private long revision;
    @Field("manifest_fingerprint")
    private String manifestFingerprint;
}
