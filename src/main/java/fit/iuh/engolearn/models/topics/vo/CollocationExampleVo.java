package fit.iuh.engolearn.models.topics.vo;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollocationExampleVo implements EmbeddedDocument {
    @Field("en")
    private String en;

    @Field("vi")
    private String vi;
}
