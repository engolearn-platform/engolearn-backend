package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpressionPurposeVo implements EmbeddedDocument {
    @Field("purpose_en")
    private String purposeEn;

    @Field("purpose_vi")
    private String purposeVi;

    @Field("order_index")
    private Integer orderIndex;

    @Field("examples")
    private List<ExpressionExampleVo> examples;
}
