package fit.iuh.engolearn.placement.contract;

import fit.iuh.engolearn.placement.contract.dto.PlacementBaselineDto;
import java.util.Optional;

/** Returns the latest valid completed Placement result owned by the supplied account. */
public interface PlacementBaselineLookup {
    Optional<PlacementBaselineDto> findForUser(String userId);
}
