package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.StartPlacementAttemptResponse;

public record PlacementStartOutcome(StartPlacementAttemptResponse response, boolean created) {
}
