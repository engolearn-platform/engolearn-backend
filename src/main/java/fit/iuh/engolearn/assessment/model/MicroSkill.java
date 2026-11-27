package fit.iuh.engolearn.assessment.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Document("micro_skills")
public class MicroSkill extends BaseDocument {
    private String label;
    private String section;
    private boolean active;
    @Field("legacy_name") private String legacyName;
}
