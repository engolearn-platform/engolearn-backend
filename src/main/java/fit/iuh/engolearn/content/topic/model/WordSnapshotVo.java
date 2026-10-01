package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.EmbeddedDocument;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordSnapshotVo implements EmbeddedDocument {
    @Field("word")
    private String word;

    @Field("pos")
    private String pos;

    @Field("sense")
    private String sense;

    @Field("describe_vi")
    private String describeVi;

    @Field("phonetic")
    private String phonetic;

    @Field("audio_url")
    private String audioUrl;
}
