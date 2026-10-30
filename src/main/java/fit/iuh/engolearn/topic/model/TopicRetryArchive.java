package fit.iuh.engolearn.topic.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("topic_retry_archive")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TopicRetryArchive extends BaseDocument {
    @Field("generation")
    private long generation;

    @Field("user_id")
    private String userId;

    @Field("topic_id")
    private String topicId;

    @Field("session_id")
    private String sessionId;

    @Field("source_version")
    private Long sourceVersion;

    @Field("attempt")
    private UserTopicItemAttempt attempt;
}
