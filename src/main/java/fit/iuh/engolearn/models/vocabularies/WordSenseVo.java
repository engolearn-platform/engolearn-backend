package fit.iuh.engolearn.models.vocabularies;

import fit.iuh.engolearn.models.shared.enums.CefrLevel;
import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordSenseVo implements EmbeddedDocument {
    @Field("pos")
    private String pos;

    @Field("meaning_vi")
    private String meaningVi;

    @Field("cefr_level")
    private CefrLevel cefrLevel;
}