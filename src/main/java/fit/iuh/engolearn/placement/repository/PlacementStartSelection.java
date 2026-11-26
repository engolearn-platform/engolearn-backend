package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAttempt;

public record PlacementStartSelection(PlacementAttempt attempt, boolean created) {
}
