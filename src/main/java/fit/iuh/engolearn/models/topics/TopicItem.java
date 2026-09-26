package fit.iuh.engolearn.models.topics;

import fit.iuh.engolearn.models.shared.BaseDocument;
import fit.iuh.engolearn.models.shared.enums.ContentStatus;
import fit.iuh.engolearn.models.topics.enums.ChangeType;
import fit.iuh.engolearn.models.topics.vo.ExpressionBlockVo;
import fit.iuh.engolearn.models.topics.vo.ItemWordVo;
import fit.iuh.engolearn.models.topics.vo.QuizModuleVo;
import fit.iuh.engolearn.models.topics.vo.TopicContextVo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document("topic_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TopicItem extends BaseDocument {
    @Field("topic_id")
    private String topicId;

    @Field("version")
    private Integer version;

    @Field("change_type")
    private ChangeType changeType;

    @Field("topic_version_id")
    private String topicVersionId;

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

    @Field("context")
    private TopicContextVo context;

    @Field("words")
    private List<ItemWordVo> words;

    @Field("expressions")
    private ExpressionBlockVo expressions;

    @Field("quizzes")
    private List<QuizModuleVo> quizzes;

    @Field("beta_at")
    private Instant betaAt;

    @Field("published_at")
    private Instant publishedAt;
}
