package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import java.time.Instant;

public record PlacementResultResponse(String attemptId, PlacementAttemptStatus status, int score,
                                      int totalQuestions, PlacementLevel placementLevel, Instant completedAt,
                                      PlacementResultSummaryResponse summary) {
}
