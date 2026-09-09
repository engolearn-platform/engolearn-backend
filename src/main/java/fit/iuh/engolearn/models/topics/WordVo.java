package fit.iuh.engolearn.models.topics;

import fit.iuh.engolearn.models.shared.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordVo implements EmbeddedDocument {
    @Field("word_id")
    private String wordId;

    @Field("word_sense_id")
    private String wordSenseId;

    @Field("word")
    private String word;

    @Field("pos")
    private String pos;

    @Field("meaning_vi")
    private String meaningVi;

    @Field("phonetic")
    private String phonetic;

    @Field("audio_url")
    private String audioUrl;

    @Field("order_index")
    private Integer orderIndex;
}