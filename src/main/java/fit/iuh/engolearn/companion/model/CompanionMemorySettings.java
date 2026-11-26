package fit.iuh.engolearn.companion.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("companion_memory_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CompanionMemorySettings extends BaseDocument {
    @Indexed(unique = true) @Field("learner_id") private String learnerId;
    @Field("automatic_memory_enabled") private Boolean automaticMemoryEnabled;
    @Field("forget_revision") private long forgetRevision;
}
