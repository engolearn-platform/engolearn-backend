package fit.iuh.engolearn.subscription.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import fit.iuh.engolearn.subscription.model.enums.*;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode
public class PublicationResultVo implements EmbeddedDocument {
    @Field("publication_id")
    private String publicationId;
    @Field("scope")
    private PolicyScope scope;
    @Field("version")
    private long version;
    @Field("effective_at")
    private Instant effectiveAt;
    @Field("accepted_at")
    private Instant acceptedAt;
    @Field("revision")
    private long revision;
}
