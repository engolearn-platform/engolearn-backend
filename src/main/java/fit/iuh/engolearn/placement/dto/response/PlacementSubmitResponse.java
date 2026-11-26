package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;

public record PlacementSubmitResponse(String attemptId, PlacementAttemptStatus status,
                                      int answeredCount, boolean claimRequired) {
}
