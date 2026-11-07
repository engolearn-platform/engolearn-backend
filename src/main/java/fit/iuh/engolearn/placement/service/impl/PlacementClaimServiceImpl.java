package fit.iuh.engolearn.placement.service.impl;

import fit.iuh.engolearn.placement.dto.response.PlacementClaimGateResponse;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.repository.PlacementAttemptRepository;
import fit.iuh.engolearn.placement.repository.PlacementClaimOutcome;
import fit.iuh.engolearn.placement.repository.PlacementClaimSelection;
import fit.iuh.engolearn.placement.service.PlacementAccessContext;
import fit.iuh.engolearn.placement.service.PlacementClaimService;
import fit.iuh.engolearn.placement.service.PlacementDeadlineService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Temporary Auth seam: authenticated claim-gate reads claim eligible Guest results.
 * Replace the trigger when Auth provides a login-success hook. */
@Service
@RequiredArgsConstructor
public class PlacementClaimServiceImpl implements PlacementClaimService {
    private final PlacementAttemptRepository attempts;
    private final PlacementDeadlineService deadlines;
    private final Clock placementClock;

    @Override
    public PlacementClaimGateResponse getClaimGate(String attemptId, PlacementAccessContext access) {
        for (;;) {
            Instant now = placementClock.instant();
            PlacementAttempt attempt = attempts.findById(attemptId).orElseThrow(PlacementClaimServiceImpl::notFound);
            requireOwner(attempt, access);
            attempt = deadlines.resolve(attempt, now);
            requireOwner(attempt, access);

            if (attempt.getStatus() == PlacementAttemptStatus.IN_PROGRESS) {
                throw new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_ATTEMPT_IN_PROGRESS",
                        "Placement attempt has not ended");
            }
            if (attempt.getStatus() == PlacementAttemptStatus.EXPIRED) {
                return response(attempt, PlacementClaimStatus.NO_RESULT, now, null);
            }
            if (attempt.getCorrectCount() == null || attempt.getPlacementLevel() == null) {
                throw new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_RESULT_UNAVAILABLE",
                        "Placement attempt does not have a valid result");
            }
            if (attempt.getUserId() != null) {
                return response(attempt, PlacementClaimStatus.ALREADY_CLAIMED, now, attempt.getId());
            }
            if (attempt.getClaimExpiresAt() == null) {
                throw new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_CLAIM_WINDOW_UNAVAILABLE",
                        "Placement claim window is unavailable");
            }
            if (!now.isBefore(attempt.getClaimExpiresAt())) {
                return response(attempt, PlacementClaimStatus.CLAIM_EXPIRED, now, null);
            }
            if (access.currentUserId() == null) {
                return response(attempt, PlacementClaimStatus.CLAIMABLE, now, null);
            }
            Optional<PlacementClaimSelection> claimed = attempts.claimIfEligible(attempt.getId(), attempt.getRevision(),
                    access.guestSessionId(), access.currentUserId(), now);
            if (claimed.isPresent()) {
                PlacementClaimSelection selection = claimed.get();
                if (selection.outcome() == PlacementClaimOutcome.USER_HAS_RESULT) {
                    return response(attempt, PlacementClaimStatus.USER_HAS_RESULT, now,
                            selection.existingUserResult().getId());
                }
                if (selection.outcome() == PlacementClaimOutcome.ACTIVE_ATTEMPT_EXISTS) {
                    if (resolveActiveUserAttempt(selection.activeUserAttempt(), now)) {
                        throw new PlacementRequestException(HttpStatus.CONFLICT, "PLACEMENT_ACTIVE_ATTEMPT_EXISTS",
                                "User has an active Placement attempt");
                    }
                    continue;
                }
                return response(selection.attempt(), PlacementClaimStatus.ALREADY_CLAIMED, now,
                        selection.attempt().getId());
            }
        }
    }

    private boolean resolveActiveUserAttempt(PlacementAttempt active, Instant now) {
        PlacementAttempt resolved;
        try {
            resolved = deadlines.resolve(active, now);
        } catch (PlacementRequestException exception) {
            if ("PLACEMENT_USER_HAS_RESULT".equals(exception.code())) return false;
            throw exception;
        }
        return resolved.getStatus() == PlacementAttemptStatus.IN_PROGRESS;
    }

    private static PlacementClaimGateResponse response(PlacementAttempt attempt, PlacementClaimStatus status,
                                                       Instant now, String resultAttemptId) {
        return new PlacementClaimGateResponse(attempt.getId(), attempt.getStatus(), status,
                attempt.getClaimExpiresAt(), now, resultAttemptId);
    }

    private static void requireOwner(PlacementAttempt attempt, PlacementAccessContext access) {
        if (attempt.getUserId() != null ? !attempt.getUserId().equals(access.currentUserId())
                : !attempt.getGuestSessionId().equals(access.guestSessionId())) throw notFound();
    }

    private static PlacementRequestException notFound() {
        return new PlacementRequestException(HttpStatus.NOT_FOUND, "PLACEMENT_ATTEMPT_NOT_FOUND",
                "Placement attempt not found");
    }
}
