package fit.iuh.engolearn.user.model.vo;

import fit.iuh.engolearn.shared.EmbeddedDocument;
import java.time.Instant;
import org.springframework.data.mongodb.core.mapping.Field;

public record UserAssessmentProgressVo(
        @Field("initial_level") String initialLevel,
        @Field("current_level") String currentLevel,
        @Field("highest_level") String highestLevel,
        @Field("latest_valid_at") Instant latestValidAt,
        @Field("latest_valid_attempt_id") String latestValidAttemptId,
        @Field("development_data") boolean developmentData
) implements EmbeddedDocument { }
