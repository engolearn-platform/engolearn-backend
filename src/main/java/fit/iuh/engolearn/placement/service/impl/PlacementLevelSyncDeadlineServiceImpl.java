package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.service.PlacementDeadlineService;
import fit.iuh.engolearn.placement.service.PlacementTransactionExecutor;
import fit.iuh.engolearn.placement.service.PlacementUserLevelSynchronizer;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class PlacementLevelSyncDeadlineServiceImpl implements PlacementDeadlineService {
    private final PlacementDeadlineService delegate;
    private final PlacementTransactionExecutor transactions;
    private final PlacementAttemptRepository attempts;
    private final PlacementUserLevelSynchronizer levels;

    @Override
    public PlacementAttempt resolve(PlacementAttempt attempt, Instant now) {
        return transactions.execute(() -> {
            PlacementAttempt fresh = current(attempt.getId());
            boolean wasActive = fresh.getStatus() == PlacementAttemptStatus.IN_PROGRESS;
            PlacementAttempt resolved = delegate.resolve(fresh, now);
            if (wasActive && resolved.getStatus() != PlacementAttemptStatus.IN_PROGRESS) levels.synchronize(resolved);
            return resolved;
        });
    }

    @Override
    public PlacementAttempt submitBeforeDeadline(PlacementAttempt attempt, Instant now) {
        return transactions.execute(() -> {
            PlacementAttempt fresh = current(attempt.getId());
            if (fresh.getStatus() != PlacementAttemptStatus.IN_PROGRESS) return fresh;
            // The caller validated answers on this revision; a concurrent save must trigger its validation loop again.
            if (fresh.getRevision() != attempt.getRevision()) return null;
            PlacementAttempt finished = delegate.submitBeforeDeadline(fresh, now);
            // One commit couples the result and its profile promotion; Guest results remain unpromoted until claim.
            levels.synchronize(finished);
            return finished;
        });
    }

    private PlacementAttempt current(String id) {
        return attempts.findById(id).orElseThrow(() -> new PlacementRequestException(HttpStatus.NOT_FOUND,
                "PLACEMENT_ATTEMPT_NOT_FOUND", "Placement attempt not found"));
    }
}
