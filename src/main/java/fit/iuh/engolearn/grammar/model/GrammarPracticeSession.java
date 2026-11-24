package fit.iuh.engolearn.grammar.model;

import fit.iuh.engolearn.grammar.model.enums.GrammarPracticeStatus;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeAnswerVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeOrderVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarPracticeResultVo;
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
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Document("grammar_practice_sessions")
@CompoundIndex(name = "grammar_learner_lesson_session", def = "{'user_id': 1, 'lesson_id': 1, 'status': 1}")
public class GrammarPracticeSession extends BaseDocument {
    @Field("user_id")
    private String userId;
    @Field("topic_id")
    private String topicId;
    @Field("lesson_id")
    private String lessonId;
    @Field("content_version")
    private int contentVersion;
    @Field("status")
    private GrammarPracticeStatus status;
    @Field("last_activity_at")
    private Instant lastActivityAt;
    @Field("question_order")
    private List<GrammarPracticeOrderVo> questionOrder;
    @Field("answers")
    private List<GrammarPracticeAnswerVo> answers;
    @Field("result")
    private GrammarPracticeResultVo result;
}
