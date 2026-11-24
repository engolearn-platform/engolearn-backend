package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.grammar.model.vo.GrammarLessonContentVo;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_published_revisions")
public class GrammarPublishedRevision extends BaseDocument {
    @Field("topic_id")
    private String topicId;
    @Field("lesson_id")
    private String lessonId;
    @Field("content_version")
    private int contentVersion;
    @Field("content")
    private GrammarLessonContentVo content;
}
