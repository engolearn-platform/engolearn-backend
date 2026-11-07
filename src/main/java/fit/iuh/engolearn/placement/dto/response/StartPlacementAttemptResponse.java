package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import java.time.Instant;

public record StartPlacementAttemptResponse(String attemptId, PlacementAttemptStatus status,
                                            Instant deadlineAt, boolean claimRequired) {
}
