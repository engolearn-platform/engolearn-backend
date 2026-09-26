package fit.iuh.engolearn.models.topics.vo;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizSentenceVo implements EmbeddedDocument {
    @Field("text_en")
    private String textEn;

    @Field("meaning_vi")
    private String meaningVi;

    @Field("audio_url")
    private String audioUrl;

    @Field("audio_url_slow")
    private String audioUrlSlow;

    @Field("source")
    private String source;

    @Field("external_id")
    private String externalId;
}