package fit.iuh.engolearn.companion.model;

import fit.iuh.engolearn.shared.BaseDocument;
import java.time.Instant;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("companion_memory_vector_cleanup")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MemoryVectorCleanup extends BaseDocument {
    @Indexed(unique = true) @Field("point_id") private String pointId;
    @Field("learner_id") private String learnerId;
    @Field("deletion_version") private long deletionVersion;
    @Field("deleted_at") private Instant deletedAt;
    @Field("retry_state") private String retryState;
}
