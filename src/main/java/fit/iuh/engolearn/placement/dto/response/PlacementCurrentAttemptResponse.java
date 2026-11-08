package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.time.Instant;

public record PlacementCurrentAttemptResponse(String attemptId, PlacementAttemptStatus status,
        Instant deadlineAt, Instant serverNow, int totalQuestions, int answeredCount,
        PlacementSkill nextSection, Integer resumeOrder, Instant lastSavedAt, boolean claimRequired) {
}
