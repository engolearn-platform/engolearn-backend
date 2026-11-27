package fit.iuh.engolearn.learningpath.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("question_pool_coverage_fences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class QuestionPoolCoverageFence extends BaseDocument {
    @Field("micro_skill")
    private String microSkill;
    @Field("level")
    private CefrLevel level;
    @Field("revision")
    private long revision;
}
