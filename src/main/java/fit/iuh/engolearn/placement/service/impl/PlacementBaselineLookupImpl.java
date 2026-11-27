package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.contract.PlacementBaselineLookup;
import fit.iuh.engolearn.placement.contract.dto.PlacementBaselineDto;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementBaselineLookupImpl implements PlacementBaselineLookup {
    private final PlacementAttemptRepository attempts;

    @Override
    public Optional<PlacementBaselineDto> findForUser(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();
        return attempts.findValidResultForUser(userId).map(result -> new PlacementBaselineDto(
                result.getId(), CefrLevel.valueOf(result.getPlacementLevel().name())));
    }
}
