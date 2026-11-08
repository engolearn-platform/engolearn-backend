package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.response.PlacementLearningResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import org.springframework.http.HttpStatus;
import fit.iuh.engolearn.placement.service.PlacementLearningService;
import fit.iuh.engolearn.placement.service.PlacementResultService;
import fit.iuh.engolearn.placement.service.PlacementTransactionExecutor;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.user.contract.UserLevelPromotion;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class PlacementLearningServiceImpl implements PlacementLearningService {
    private final PlacementResultService results;
    private final PlacementAttemptRepository attempts;
    private final PlacementTransactionExecutor transactions;
    private final UserLevelPromotion userLevels;

    @Override
    public PlacementLearningResponse activate(String attemptId, String userId) {
        return transactions.execute(() -> {
            var owned = attempts.findById(attemptId).filter(a -> userId != null && userId.equals(a.getUserId()))
                    .orElseThrow(() -> new PlacementRequestException(HttpStatus.NOT_FOUND,
                            "PLACEMENT_ATTEMPT_NOT_FOUND", "Placement attempt not found"));
            // Learning activation accepts an already-final result; it must not itself submit a running attempt.
            if (owned.getStatus() != PlacementAttemptStatus.SUBMITTED && owned.getStatus() != PlacementAttemptStatus.AUTO_SUBMITTED) {
                throw new PlacementRequestException(HttpStatus.NOT_FOUND, "PLACEMENT_RESULT_NOT_AVAILABLE", "Placement result is not available");
            }
            var result = results.getResult(attemptId, userId);
            // Historical results are reconciled by this explicit command, not by merely viewing a result.
            CefrLevel actual = userLevels.promote(userId, CefrLevel.valueOf(result.placementLevel().name()));
            return new PlacementLearningResponse(attemptId, actual);
        });
    }
}
