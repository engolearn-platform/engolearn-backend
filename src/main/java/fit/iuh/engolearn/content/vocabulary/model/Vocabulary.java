package fit.iuh.engolearn.content.vocabulary.model;

import fit.iuh.engolearn.shared.model.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document("vocabularies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Vocabulary extends BaseDocument {
    @Field("word")
    private String word;

    @Field("phonetic")
    private String phonetic;

    @Field("audio_url")
    private String audioUrl;

    @Field("source_context_tag")
    private String sourceContextTag;

    @Field("source")
    private String source;

    @Field("external_id")
    private String externalId;

    @Field("senses")
    private List<WordSenseVo> senses;

    @Field("example_sentences")
    private List<ExampleSentenceVo> exampleSentences;
}