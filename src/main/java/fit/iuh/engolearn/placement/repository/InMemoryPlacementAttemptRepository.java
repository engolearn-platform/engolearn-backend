package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.vo.PlacementAnswerVo;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode",
        havingValue = "sample", matchIfMissing = true)
public class InMemoryPlacementAttemptRepository implements PlacementAttemptRepository {
    private final ConcurrentHashMap<String, PlacementAttempt> attempts = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<PlacementAttempt> findById(String id) {
        return Optional.ofNullable(attempts.get(id)).map(PlacementCopies::attempt);
    }

    @Override
    public synchronized Optional<PlacementAttempt> findActiveForUser(String userId) {
        return findLatest(userId, null, PlacementAttemptStatus.IN_PROGRESS);
    }

    @Override
    public synchronized Optional<PlacementAttempt> findActiveForGuest(String guestSessionId) {
        return findLatest(null, guestSessionId, PlacementAttemptStatus.IN_PROGRESS);
    }

    @Override
    public synchronized Optional<PlacementAttempt> findValidResultForUser(String userId) {
        return attempts.values().stream().filter(a -> userId.equals(a.getUserId()) && validResult(a))
                .max(Comparator.comparing(PlacementAttempt::getStartedAt)).map(PlacementCopies::attempt);
    }

    @Override
    public synchronized Optional<PlacementAttempt> findClaimableForGuest(String guestSessionId, Instant now) {
        return attempts.values().stream().filter(a -> guestSessionId.equals(a.getGuestSessionId())
                        && a.getUserId() == null && validResult(a) && a.getClaimExpiresAt().isAfter(now))
                .max(Comparator.comparing(PlacementAttempt::getStartedAt)).map(PlacementCopies::attempt);
    }

    @Override
    public synchronized boolean isKnownGuestSession(String guestSessionId) {
        return guestSessionId != null && attempts.values().stream()
                .anyMatch(a -> guestSessionId.equals(a.getGuestSessionId()));
    }

    @Override
    public synchronized PlacementStartSelection startIfEligible(PlacementAttempt proposed, String guestSessionId, Instant now) {
        attempts.values().stream().filter(a -> guestSessionId != null
                        && guestSessionId.equals(a.getStartSessionKey()) && a.getUserId() == null && validResult(a)
                        && a.getClaimExpiresAt() != null && !now.isBefore(a.getClaimExpiresAt()))
                .forEach(a -> { a.setStartSessionKey(null); a.setRevision(a.getRevision() + 1); });
        String user = proposed.getUserId();
        Optional<PlacementAttempt> existing = guestSessionId == null ? Optional.empty()
                : findActiveForGuest(guestSessionId);
        if (existing.isEmpty() && user != null) existing = findValidResultForUser(user);
        if (existing.isEmpty() && user != null) existing = findActiveForUser(user);
        if (existing.isEmpty() && guestSessionId != null) existing = findClaimableForGuest(guestSessionId, now);
        if (existing.isEmpty() && guestSessionId != null) existing = attempts.values().stream()
                .filter(a -> guestSessionId.equals(a.getStartSessionKey())).findFirst().map(PlacementCopies::attempt);
        if (existing.isPresent()) return new PlacementStartSelection(existing.get(), false);
        PlacementAttempt copy = PlacementCopies.attempt(proposed);
        copy.setStartSessionKey(guestSessionId);
        if (attempts.putIfAbsent(copy.getId(), copy) != null) {
            throw new IllegalStateException("Placement attempt ID collision");
        }
        return new PlacementStartSelection(PlacementCopies.attempt(copy), true);
    }

    @Override
    public synchronized Optional<PlacementClaimSelection> claimIfEligible(String attemptId, long expectedRevision,
                                                                           String guestSessionId, String userId, Instant now) {
        PlacementAttempt stored = attempts.get(attemptId);
        if (stored == null || stored.getRevision() != expectedRevision || stored.getUserId() != null
                || guestSessionId == null || !guestSessionId.equals(stored.getGuestSessionId())
                || !validResult(stored) || stored.getClaimExpiresAt() == null
                || !now.isBefore(stored.getClaimExpiresAt())) return Optional.empty();
        Optional<PlacementAttempt> existing = findValidResultForUser(userId);
        if (existing.isPresent()) {
            return Optional.of(new PlacementClaimSelection(PlacementClaimOutcome.USER_HAS_RESULT,
                    PlacementCopies.attempt(stored), existing.get(), null));
        }
        Optional<PlacementAttempt> active = findActiveForUser(userId);
        if (active.isPresent()) {
            return Optional.of(new PlacementClaimSelection(PlacementClaimOutcome.ACTIVE_ATTEMPT_EXISTS,
                    PlacementCopies.attempt(stored), null, active.get()));
        }
        PlacementAttempt claimed = PlacementCopies.attempt(stored);
        claimed.setUserId(userId);
        claimed.setStartSessionKey(null);
        claimed.setRevision(expectedRevision + 1);
        attempts.put(attemptId, claimed);
        return Optional.of(new PlacementClaimSelection(PlacementClaimOutcome.CLAIMED,
                PlacementCopies.attempt(claimed), null, null));
    }

    @Override
    public synchronized Optional<PlacementAttempt> saveAnswer(String id, long expectedRevision, String questionId,
                                                               String selectedOptionId, Instant answeredAt) {
        PlacementAttempt stored = attempts.get(id);
        if (stored == null || stored.getRevision() != expectedRevision
                || stored.getStatus() != PlacementAttemptStatus.IN_PROGRESS
                || !answeredAt.isBefore(stored.getDeadlineAt())) return Optional.empty();
        PlacementAttempt next = PlacementCopies.attempt(stored);
        next.getAnswers().removeIf(a -> a.getQuestionId().equals(questionId));
        if (selectedOptionId != null) {
            next.getAnswers().add(new PlacementAnswerVo(questionId, selectedOptionId, answeredAt));
        }
        next.setRevision(expectedRevision + 1);
        attempts.put(id, next);
        return Optional.of(PlacementCopies.attempt(next));
    }

    @Override
    public synchronized Optional<PlacementAttempt> finish(String id, long expectedRevision, PlacementAttemptStatus status,
                                                           Instant endedAt, Integer correctCount, PlacementLevel level,
                                                           Instant claimExpiresAt) {
        PlacementAttempt stored = attempts.get(id);
        if (stored == null || stored.getRevision() != expectedRevision
                || stored.getStatus() != PlacementAttemptStatus.IN_PROGRESS) return Optional.empty();
        if (status != PlacementAttemptStatus.EXPIRED && stored.getUserId() != null
                && findValidResultForUser(stored.getUserId()).isPresent()) return Optional.empty();
        PlacementAttempt next = PlacementCopies.attempt(stored);
        next.setStatus(status);
        next.setEndedAt(endedAt);
        next.setCorrectCount(correctCount);
        next.setPlacementLevel(level);
        next.setClaimExpiresAt(claimExpiresAt);
        if (stored.getUserId() != null || status == PlacementAttemptStatus.EXPIRED) next.setStartSessionKey(null);
        next.setRevision(expectedRevision + 1);
        attempts.put(id, next);
        return Optional.of(PlacementCopies.attempt(next));
    }

    private Optional<PlacementAttempt> findLatest(String userId, String guestSessionId, PlacementAttemptStatus status) {
        return attempts.values().stream().filter(a -> a.getStatus() == status
                        && (userId != null ? userId.equals(a.getUserId()) :
                        guestSessionId != null && guestSessionId.equals(a.getGuestSessionId()) && a.getUserId() == null))
                .max(Comparator.comparing(PlacementAttempt::getStartedAt)).map(PlacementCopies::attempt);
    }

    private boolean validResult(PlacementAttempt attempt) {
        return (attempt.getStatus() == PlacementAttemptStatus.SUBMITTED
                || attempt.getStatus() == PlacementAttemptStatus.AUTO_SUBMITTED)
                && attempt.getCorrectCount() != null && attempt.getPlacementLevel() != null;
    }
}
