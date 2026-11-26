package fit.iuh.engolearn.companion.model;

import fit.iuh.engolearn.companion.model.enums.MemoryCategory;
import fit.iuh.engolearn.companion.model.enums.MemoryRetrievalMode;
import fit.iuh.engolearn.companion.model.enums.MemorySource;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("companion_memories")
@CompoundIndex(name = "owner_updated_id", def = "{'learner_id':1,'updated_at':-1,'_id':-1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CompanionMemory extends BaseDocument {
    @Field("learner_id") private String learnerId;
    @Field("category") private MemoryCategory category;
    @Field("content") private String content;
    @Field("source") private MemorySource source;
    @Field("retrieval_mode") private MemoryRetrievalMode retrievalMode;
    @Field("source_turn_id") private String sourceTurnId;
    @Field("version") private long version;
    @Field("vector_sync_state") private String vectorSyncState;
}
