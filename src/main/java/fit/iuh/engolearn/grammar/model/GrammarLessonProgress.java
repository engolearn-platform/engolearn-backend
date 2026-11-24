package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.grammar.model.enums.GrammarLearningStatus;
import fit.iuh.engolearn.shared.BaseDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_lesson_progress")
@CompoundIndex(name = "grammar_learner_topic_progress", def = "{'user_id': 1, 'topic_id': 1}")
public class GrammarLessonProgress extends BaseDocument {
    @Field("user_id")
    private String userId;
    @Field("topic_id")
    private String topicId;
    @Field("lesson_id")
    private String lessonId;
    @Field("status")
    private GrammarLearningStatus status;
    @Field("last_seen_content_version")
    private Integer lastSeenContentVersion;
    @Field("last_studied_at")
    private Instant lastStudiedAt;
    @Field("first_completed_at")
    private Instant firstCompletedAt;
    @Field("pending_completion_at")
    private Instant pendingCompletionAt;
    @Field("attempt_count")
    private int attemptCount;
    @Field("last_score")
    private Integer lastScore;
    @Field("best_score")
    private Integer bestScore;
}
