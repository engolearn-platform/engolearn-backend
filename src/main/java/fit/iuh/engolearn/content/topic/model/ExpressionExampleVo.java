package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.EmbeddedDocument;
import fit.iuh.engolearn.content.topic.model.ExpressionType;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpressionExampleVo implements EmbeddedDocument {
    @Field("type")
    private ExpressionType type;

    @Field("text_en")
    private String textEn;

    @Field("text_vi")
    private String textVi;

    @Field("usage_note")
    private String usageNote;

    @Field("audio_url")
    private String audioUrl;

    @Field("linked_vocab_ids")
    private List<String> linkedVocabIds;
}
