package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Version;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_topics")
@CompoundIndex(name = "grammar_level_order", def = "{'level': 1, 'order': 1}")
public class GrammarTopic extends BaseDocument {
    @Indexed(unique = true)
    @Field("code")
    private String code;
    @Field("title_vi")
    private String titleVi;
    @Field("title_en")
    private String titleEn;
    @Field("description")
    private String description;
    @Field("level")
    private GrammarLevel level;
    @Field("status")
    private GrammarStatus status;
    @Field("order")
    private Integer order;
    @Field("order_before_archive")
    private Integer orderBeforeArchive;
    @Field("first_published_at")
    private Instant firstPublishedAt;
    @Field("lesson_sequence")
    private long lessonSequence;
    @Version
    @Field("version")
    private Long version;
    @Field("learner_publication_fence")
    private long learnerPublicationFence;
    @Field("schema_version")
    private Integer schemaVersion;
    @Transient
    private boolean lessonsLoaded;
    @Builder.Default
    @Field("lessons")
    private List<GrammarLesson> lessons = new ArrayList<>();
}
