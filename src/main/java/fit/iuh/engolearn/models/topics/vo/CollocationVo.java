package fit.iuh.engolearn.models.topics.vo;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollocationVo implements EmbeddedDocument {
    @Field("text")
    private String text;

    @Field("example")
    private CollocationExampleVo example;

    @Field("description_vi")
    private String descriptionVi;
}
