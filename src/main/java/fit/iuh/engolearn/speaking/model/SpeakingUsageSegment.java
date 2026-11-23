package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDate;

@Document("speaking_usage_segments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SpeakingUsageSegment extends BaseDocument {
    public enum Kind { LEARNER_SPEECH, AI_WAIT, AI_REPLY }

    @Field("learner_id") private String learnerId;
    @Field("session_id") private String sessionId;
    @Field("turn_id") private String turnId;
    @Field("day") private LocalDate day;
    @Field("kind") private Kind kind;
    @Field("started_at") private Instant startedAt;
    @Field("ended_at") private Instant endedAt;
    @Field("duration_millis") private long durationMillis;
}
