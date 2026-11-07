package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import java.time.Instant;
import java.util.Optional;

public interface PlacementAttemptRepository {
    Optional<PlacementAttempt> findById(String id);
    Optional<PlacementAttempt> findActiveForUser(String userId);
    Optional<PlacementAttempt> findActiveForGuest(String guestSessionId);
    Optional<PlacementAttempt> findValidResultForUser(String userId);
    Optional<PlacementAttempt> findClaimableForGuest(String guestSessionId, Instant now);
    boolean isKnownGuestSession(String guestSessionId);
    PlacementStartSelection startIfEligible(PlacementAttempt proposed, String guestSessionId, Instant now);
    Optional<PlacementClaimSelection> claimIfEligible(String attemptId, long expectedRevision,
                                                       String guestSessionId, String userId, Instant now);
    Optional<PlacementAttempt> saveAnswer(String id, long expectedRevision, String questionId,
                                          String selectedOptionId, Instant answeredAt);
    Optional<PlacementAttempt> finish(String id, long expectedRevision, PlacementAttemptStatus status,
                                      Instant endedAt, Integer correctCount, PlacementLevel level, Instant claimExpiresAt);
}
