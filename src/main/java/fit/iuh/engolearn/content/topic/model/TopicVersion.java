package fit.iuh.engolearn.content.topic.model;

import fit.iuh.engolearn.shared.model.BaseDocument;
import fit.iuh.engolearn.shared.model.ContentStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document("topics_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TopicVersion extends BaseDocument {
    @Field("topic_id")
    private String topicId;

    @Field("version")
    private Integer version;

    @Field("previous_version_id")
    private String previousVersionId;

    @Field("status")
    private ContentStatus status;

    @Field("published_at")
    private Instant publishedAt;
}