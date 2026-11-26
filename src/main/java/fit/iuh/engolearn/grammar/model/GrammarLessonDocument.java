package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.grammar.model.enums.GrammarArchiveReason;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.grammar.model.vo.GrammarLessonContentVo;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_lessons")
@CompoundIndex(name = "grammar_lesson_topic_order", def = "{'topic_id': 1, 'order': 1}")
public class GrammarLessonDocument extends BaseDocument {
    @Field("topic_id")
    private String topicId;
    @Field("schema_version")
    private Integer schemaVersion;
    @Indexed(unique = true)
    @Field("code")
    private String code;
    @Field("order")
    private Integer order;
    @Field("order_before_archive")
    private Integer orderBeforeArchive;
    @Field("status")
    private GrammarStatus status;
    @Field("status_before_archive")
    private GrammarStatus statusBeforeArchive;
    @Field("archive_reason")
    private GrammarArchiveReason archiveReason;
    @Field("version")
    private long version;
    @Field("content_version")
    private int contentVersion;
    @Field("first_published_at")
    private Instant firstPublishedAt;
    @Field("ready")
    private boolean ready;
    @Field("draft")
    private GrammarLessonContentVo draft;
    @Field("published")
    private GrammarLessonContentVo published;
}
