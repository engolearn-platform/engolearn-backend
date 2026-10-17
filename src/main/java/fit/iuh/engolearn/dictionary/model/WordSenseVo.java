package fit.iuh.engolearn.dictionary.model;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.shared.EmbeddedDocument;
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