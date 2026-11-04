package fit.iuh.engolearn.speaking.model;

import fit.iuh.engolearn.shared.BaseDocument;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document("speaking_assessments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SpeakingAssessmentRecord extends BaseDocument {
    public record GoalEvidence(String goalId, String quote) {}
    public record ResourceEvidence(String resourceId, String quote, String explanation) {}
    public record Correction(String original, String corrected, String explanation) {}

    @Field("session_id") private String sessionId;
    @Field("learner_id") private String learnerId;
    @Field("transcript") private String transcript;
    @Field("criteria_version") private String criteriaVersion;
    @Field("goal_evidence") private List<GoalEvidence> goalEvidence;
    @Field("resource_evidence") private List<ResourceEvidence> resourceEvidence;
    @Field("corrections") private List<Correction> corrections;
    @Field("hint") private String hint;
    @Field("hint_example_en") private String hintExampleEn;
    @Field("hint_lead_vi") private String hintLeadVi;
    @Field("feedback") private List<String> feedback;
}
