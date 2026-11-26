package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.service.PlacementUserLevelSynchronizer;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.contract.UserLevelPromotion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlacementUserLevelSynchronizerImpl implements PlacementUserLevelSynchronizer {
    private final UserLevelPromotion userLevels;

    @Override
    public void synchronize(PlacementAttempt attempt) {
        if (attempt == null || attempt.getUserId() == null || attempt.getPlacementLevel() == null
                || attempt.getCorrectCount() == null || attempt.getEndedAt() == null
                || (attempt.getStatus() != PlacementAttemptStatus.SUBMITTED
                    && attempt.getStatus() != PlacementAttemptStatus.AUTO_SUBMITTED)) return;
        // User owns level persistence; Placement never writes another feature's collection.
        userLevels.promote(attempt.getUserId(), CefrLevel.valueOf(attempt.getPlacementLevel().name()));
    }
}
