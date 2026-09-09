package fit.iuh.engolearn.models.vocabularies;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExampleSentenceVo implements EmbeddedDocument {
    @Field("sentence_en")
    private String sentenceEn;

    @Field("sentence_vi")
    private String sentenceVi;

    @Field("source")
    private String source;

    @Field("external_id")
    private String externalId;
}