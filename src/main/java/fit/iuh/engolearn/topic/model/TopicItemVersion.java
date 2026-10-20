package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.QuizModuleVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document("topic_item_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TopicItemVersion extends BaseDocument {
    @Field("topic_item_id")
    private String topicItemId;

    @Field("topic_id")
    private String topicId;

    @Field("topic_version_id")
    private String topicVersionId;

    @Field("version")
    private Integer version;

    @Field("change_type")
    private ChangeType changeType;

    @Field("previous_item_version_id")
    private String previousItemVersionId;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("order")
    private Integer order;

    @Field("status")
    private ContentStatus status;

    @Field("duration_minutes")
    private Integer durationMinutes;

    @Field("context")
    private TopicContextVo context;

    @Field("vocabularies")
    private List<ItemWordVo> vocabularies;

    @Field("expressions")
    private List<ExpressionBlockVo> expressions;

    @Field("quizzes")
    private List<QuizModuleVo> quizzes;

    @Field("published_at")
    private Instant publishedAt;
}
