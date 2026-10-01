package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.BaseDocument;
import fit.iuh.engolearn.shared.model.ContentStatus;
import fit.iuh.engolearn.content.topic.model.ChangeType;
import fit.iuh.engolearn.content.topic.model.ExpressionBlockVo;
import fit.iuh.engolearn.content.topic.model.ItemWordVo;
import fit.iuh.engolearn.content.topic.model.QuizModuleVo;
import fit.iuh.engolearn.content.topic.model.TopicContextVo;
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
    @Field("topic_item_group_id")
    private String topicItemGroupId;

    @Field("topic_item_id")
    private String topicItemId;

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
