package fit.iuh.engolearn.models.topics.vo;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DialogueTurnVo implements EmbeddedDocument {
    @Field("speaker")
    private String speaker;

    @Field("text_en")
    private String textEn;

    @Field("text_vi")
    private String textVi;

    @Field("audio_url")
    private String audioUrl;
}
