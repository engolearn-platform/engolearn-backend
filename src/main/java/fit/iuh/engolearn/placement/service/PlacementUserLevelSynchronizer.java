package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.model.PlacementAttempt;

public interface PlacementUserLevelSynchronizer {
    void synchronize(PlacementAttempt attempt);
}
