package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementTestSet;
import java.util.Optional;

public interface PlacementTestSetRepository {
    Optional<PlacementTestSet> findPublished();
    Optional<PlacementTestSet> findVersion(String id, int version);
}
