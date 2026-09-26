package fit.iuh.engolearn.models.topics.vo;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpressionBlockVo implements EmbeddedDocument {
    @Field("purposes")
    private List<ExpressionPurposeVo> purposes;

    @Field("tips")
    private String tips;
}
