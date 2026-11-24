package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import java.time.Instant;

public record PlacementAdminAttemptSummaryResponse(
        String id,
        String name,
        Kind kind,
        String userId,
        String testSetId,
        String testSetCode,
        int testSetVersion,
        Instant startedAt,
        PlacementAttemptStatus status,
        Integer correctCount,
        Integer totalQuestions,
        PlacementLevel level,
        PlacementClaimStatus claimStatus
) {
    public enum Kind {
        USER, GUEST
    }
}
