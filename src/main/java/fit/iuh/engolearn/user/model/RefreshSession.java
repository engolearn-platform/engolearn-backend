package fit.iuh.engolearn.user.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@Document("refresh_sessions")
public class RefreshSession extends BaseDocument {
    @Indexed(unique = true) @Field("session_id") private String sessionId;
    @Field("account_id") private String accountId;
    @Field("token_hash") private String tokenHash;
    @Field("expires_at") private Instant expiresAt;
    @Field("revoked") private boolean revoked;
    @Field("generation") private long generation;
}
