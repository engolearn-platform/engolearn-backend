package fit.iuh.engolearn.models.topics;

import fit.iuh.engolearn.models.shared.BaseDocument;
import fit.iuh.engolearn.models.shared.enums.ContentStatus;
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
    @Field("version")
    private Integer version;

    @Field("previous_version_id")
    private String previousVersionId;

    @Field("topic_version_id")
    private String topicVersionId;

    @Field("topic_id")
    private String topicId;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("order")
    private Integer order;

    @Field("status")
    private ContentStatus status;

    @Field("words")
    private List<WordVo> words;

    @Field("quizzes")
    private List<QuizItemVo> quizzes;

    @Field("published_at")
    private Instant publishedAt;
}