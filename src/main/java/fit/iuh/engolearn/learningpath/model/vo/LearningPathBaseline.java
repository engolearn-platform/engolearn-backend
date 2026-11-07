package fit.iuh.engolearn.learningpath.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPathBaseline implements EmbeddedDocument {
    @Field("correct")
    private Integer correct;
    @Field("total")
    private Integer total;
    @Field("percent")
    private Double percent;
    @Field("band")
    private String band;
    @Field("skill_code")
    private String skillCode;
}
