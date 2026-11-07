package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import java.time.Instant;

public record PlacementClaimGateResponse(String attemptId, PlacementAttemptStatus attemptStatus,
                                         PlacementClaimStatus claimStatus, Instant claimExpiresAt,
                                         Instant serverNow, String resultAttemptId) {
}
