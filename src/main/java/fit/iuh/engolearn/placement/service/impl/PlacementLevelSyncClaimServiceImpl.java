package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.response.PlacementClaimGateResponse;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.service.PlacementAccessContext;
import fit.iuh.engolearn.placement.service.PlacementClaimService;
import fit.iuh.engolearn.placement.service.PlacementTransactionExecutor;
import fit.iuh.engolearn.placement.service.PlacementUserLevelSynchronizer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PlacementLevelSyncClaimServiceImpl implements PlacementClaimService {
    private final PlacementClaimService delegate;
    private final PlacementTransactionExecutor transactions;
    private final PlacementAttemptRepository attempts;
    private final PlacementUserLevelSynchronizer levels;

    @Override
    public PlacementClaimGateResponse getClaimGate(String attemptId, PlacementAccessContext access) {
        return transactions.execute(() -> {
            PlacementClaimGateResponse result = delegate.getClaimGate(attemptId, access);
            if (access.currentUserId() != null && result.resultAttemptId() != null) {
                // USER_HAS_RESULT refers to the existing owned result, never the discarded Guest score.
                attempts.findById(result.resultAttemptId())
                        .filter(owned -> access.currentUserId().equals(owned.getUserId()))
                        .ifPresent(levels::synchronize);
            }
            return result;
        });
    }
}
