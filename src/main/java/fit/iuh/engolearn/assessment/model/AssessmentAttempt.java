package fit.iuh.engolearn.assessment.model;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;
import fit.iuh.engolearn.assessment.model.vo.*;
import fit.iuh.engolearn.assessment.model.enums.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@lombok.experimental.SuperBuilder
@Document("assessment_attempts")
@CompoundIndexes({
 @CompoundIndex(name="assessment_start_idempotency", def="{'user_id':1,'request_id':1}", unique=true),
 @CompoundIndex(name="assessment_one_active", def="{'user_id':1}", unique=true, partialFilter="{'status':'ACTIVE'}"),
 @CompoundIndex(name="assessment_history", def="{'user_id':1,'submitted_at':-1,'_id':-1}"),
 @CompoundIndex(name="assessment_due", def="{'status':1,'next_due_at':1}")})
public class AssessmentAttempt extends fit.iuh.engolearn.shared.BaseDocument {
    @Version @Field("version") private Long version;
    @Field("user_id") private String userId;
    @Field("request_id") private String requestId;
    @Field("binding_hash") private String bindingHash;
    @Field("status") @Builder.Default private AssessmentStatus status = AssessmentStatus.ACTIVE;
    @Field("started_at") private Instant startedAt;
    @Field("submitted_at") private Instant submittedAt;
    @Field("next_due_at") private Instant nextDueAt;
    @Field("unit_started_at") private Instant unitStartedAt;
    @Field("unit_index") private int unitIndex;
    @Field("cursor") private int cursor;
    @Field("phase") private AssessmentPhase phase;
    @Field("paper") private AssessmentPaperSnapshotVo paper;
    @Field("answers") @Builder.Default private Map<String,Integer> answers = new HashMap<>();
    @Field("answer_sequences") @Builder.Default private Map<String,Long> answerSequences = new HashMap<>();
    @Field("commands") @Builder.Default private Map<String,String> commands = new HashMap<>();
    @Field("audio_issues") @Builder.Default private Map<String,String> audioIssues = new HashMap<>();
    @Field("audio_failures") @Builder.Default private Map<String,String> audioFailures = new HashMap<>();
    @Field("analysis_grants") @Builder.Default private Set<String> analysisGrants = new HashSet<>();
    @Field("skill_results") @Builder.Default private List<AssessmentSkillResultVo> skillResults = List.of();
    @Field("correct_count") private Integer correctCount;
    @Field("reading_correct") private Integer readingCorrect;
    @Field("listening_correct") private Integer listeningCorrect;
    @Field("level") private String level;
}
