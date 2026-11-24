package fit.iuh.engolearn.placement.repository;

import java.util.List;
import java.util.Set;

public interface PlacementAttemptTestSetRepository {
    List<PlacementAttemptTestSetVersion> findVersionsByCode(String code, Integer version);
    List<PlacementAttemptTestSetVersion> findVersionsById(Set<String> ids);
}
