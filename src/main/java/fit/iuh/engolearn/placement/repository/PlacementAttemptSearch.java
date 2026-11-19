package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import java.time.Instant;
import java.util.List;

public record PlacementAttemptSearch(
        String search,
        String userId,
        List<PlacementAttemptTestSetVersion> testSetVersions,
        Integer version,
        PlacementAttemptStatus status,
        PlacementLevel level,
        Instant startedAtFrom,
        Instant startedAtBefore
) {
}
