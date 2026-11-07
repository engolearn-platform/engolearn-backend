package fit.iuh.engolearn.assessment.model;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@lombok.experimental.SuperBuilder
@Document("assessment_profiles")
@CompoundIndex(name="assessment_profile_owner", def="{'user_id':1}", unique=true)
public class AssessmentProfile extends fit.iuh.engolearn.shared.BaseDocument {
    @Field("user_id") private String userId;
    @Field("initial_level") private String initialLevel;
    @Field("current_level") private String currentLevel;
    @Field("highest_level") private String highestLevel;
    @Field("latest_valid_at") private Instant latestValidAt;
    @Field("latest_valid_attempt_id") private String latestValidAttemptId;
    @Field("development_data") private boolean developmentData;
}
