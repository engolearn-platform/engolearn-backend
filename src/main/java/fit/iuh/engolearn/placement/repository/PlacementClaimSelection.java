package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAttempt;

public record PlacementClaimSelection(PlacementClaimOutcome outcome, PlacementAttempt attempt,
                                      PlacementAttempt existingUserResult, PlacementAttempt activeUserAttempt) {
}
