package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import java.time.Instant;

public interface PlacementDeadlineService {
    PlacementAttempt resolve(PlacementAttempt attempt, Instant now);
    PlacementAttempt submitBeforeDeadline(PlacementAttempt attempt, Instant now);
}
