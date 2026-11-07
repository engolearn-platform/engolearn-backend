package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import java.util.List;

public record PlacementReviewResponse(String attemptId, PlacementAttemptStatus status, int totalQuestions,
                                      int score, PlacementLevel placementLevel, int incorrectCount,
                                      int unansweredCount, List<PlacementReviewQuestionResponse> questions) {
}
