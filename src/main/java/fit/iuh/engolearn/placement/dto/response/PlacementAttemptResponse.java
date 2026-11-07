package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import java.time.Instant;
import java.util.List;

public record PlacementAttemptResponse(String attemptId, PlacementAttemptStatus status, Instant startedAt,
                                       Instant deadlineAt, Instant serverNow, int totalQuestions,
                                       List<PlacementQuestionResponse> questions,
                                       List<PlacementSavedAnswerResponse> answers, boolean claimRequired) {
}
