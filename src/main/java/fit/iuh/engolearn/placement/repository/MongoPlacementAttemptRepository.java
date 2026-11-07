package fit.iuh.engolearn.placement.repository;

import fit.iuh.engolearn.placement.model.PlacementAttempt;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.vo.PlacementAnswerVo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "engolearn.placement.persistence", name = "mode", havingValue = "mongo")
public class MongoPlacementAttemptRepository implements PlacementAttemptRepository {
    private static final int START_RETRIES = 4;

    private final MongoTemplate mongo;

    @Override
    public Optional<PlacementAttempt> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, PlacementAttempt.class));
    }

    @Override
    public Optional<PlacementAttempt> findActiveForUser(String userId) {
        return findLatest(Criteria.where("user_id").is(userId)
                .and("status").is(PlacementAttemptStatus.IN_PROGRESS));
    }

    @Override
    public Optional<PlacementAttempt> findActiveForGuest(String guestSessionId) {
        return findLatest(Criteria.where("guest_session_id").is(guestSessionId)
                .and("user_id").is(null)
                .and("status").is(PlacementAttemptStatus.IN_PROGRESS));
    }

    @Override
    public Optional<PlacementAttempt> findValidResultForUser(String userId) {
        return findLatest(validResultCriteria().and("user_id").is(userId));
    }

    @Override
    public Optional<PlacementAttempt> findClaimableForGuest(String guestSessionId, Instant now) {
        return findLatest(validResultCriteria()
                .and("guest_session_id").is(guestSessionId)
                .and("user_id").is(null)
                .and("claim_expires_at").gt(now));
    }

    @Override
    public boolean isKnownGuestSession(String guestSessionId) {
        return guestSessionId != null && mongo.exists(
                Query.query(Criteria.where("guest_session_id").is(guestSessionId)), PlacementAttempt.class);
    }

    @Override
    public PlacementStartSelection startIfEligible(PlacementAttempt proposed, String guestSessionId, Instant now) {
        proposed.setStartSessionKey(guestSessionId);
        for (int attempt = 0; attempt < START_RETRIES; attempt++) {
            releaseExpiredSession(guestSessionId, now);
            Optional<PlacementAttempt> existing = findStartBlocker(proposed, guestSessionId, now);
            if (existing.isPresent()) return new PlacementStartSelection(existing.get(), false);
            try {
                if (proposed.getCreatedAt() == null) proposed.setCreatedAt(now);
                if (proposed.getUpdatedAt() == null) proposed.setUpdatedAt(now);
                mongo.insert(proposed);
                return new PlacementStartSelection(proposed, true);
            } catch (DuplicateKeyException ignored) {
                // Identity and session reservations are acquired by the same indexed insert.
            }
        }
        Optional<PlacementAttempt> blocker = findStartBlocker(proposed, guestSessionId, now);
        if (blocker.isPresent()) return new PlacementStartSelection(blocker.get(), false);
        throw new IllegalStateException("Placement attempt start could not be serialized");
    }

    @Override
    public Optional<PlacementClaimSelection> claimIfEligible(String attemptId, long expectedRevision,
                                                             String guestSessionId, String userId, Instant now) {
        PlacementAttempt guestAttempt = mongo.findById(attemptId, PlacementAttempt.class);
        Optional<PlacementAttempt> existing = findValidResultForUser(userId);
        if (existing.isPresent()) {
            return Optional.of(new PlacementClaimSelection(PlacementClaimOutcome.USER_HAS_RESULT,
                    guestAttempt, existing.get(), null));
        }
        Optional<PlacementAttempt> active = findActiveForUser(userId);
        if (active.isPresent()) {
            return Optional.of(new PlacementClaimSelection(PlacementClaimOutcome.ACTIVE_ATTEMPT_EXISTS,
                    guestAttempt, null, active.get()));
        }
        Criteria criteria = Criteria.where("_id").is(attemptId)
                .and("revision").is(expectedRevision)
                .and("guest_session_id").is(guestSessionId)
                .and("user_id").is(null)
                .and("status").in(PlacementAttemptStatus.SUBMITTED, PlacementAttemptStatus.AUTO_SUBMITTED)
                .and("claim_expires_at").gt(now)
                .and("correct_count").ne(null)
                .and("placement_level").ne(null);
        Update update = new Update().set("user_id", userId).unset("start_session_key")
                .set("updated_at", now).inc("revision", 1);
        try {
            if (mongo.updateFirst(Query.query(criteria), update, PlacementAttempt.class).getModifiedCount() != 1) {
                return Optional.empty();
            }
            PlacementAttempt claimed = mongo.findById(attemptId, PlacementAttempt.class);
            return claimed == null ? Optional.empty() : Optional.of(new PlacementClaimSelection(
                    PlacementClaimOutcome.CLAIMED, claimed, null, null));
        } catch (DuplicateKeyException ignored) {
            PlacementAttempt currentGuest = mongo.findById(attemptId, PlacementAttempt.class);
            Optional<PlacementAttempt> currentResult = findValidResultForUser(userId);
            if (currentResult.isPresent()) {
                return Optional.of(new PlacementClaimSelection(PlacementClaimOutcome.USER_HAS_RESULT,
                        currentGuest, currentResult.get(), null));
            }
            Optional<PlacementAttempt> currentActive = findActiveForUser(userId);
            if (currentActive.isPresent()) {
                return Optional.of(new PlacementClaimSelection(PlacementClaimOutcome.ACTIVE_ATTEMPT_EXISTS,
                        currentGuest, null, currentActive.get()));
            }
            return Optional.empty();
        }
    }

    @Override
    public Optional<PlacementAttempt> saveAnswer(String id, long expectedRevision, String questionId,
                                                 String selectedOptionId, Instant answeredAt) {
        PlacementAttempt stored = mongo.findById(id, PlacementAttempt.class);
        if (stored == null || stored.getRevision() != expectedRevision
                || stored.getStatus() != PlacementAttemptStatus.IN_PROGRESS
                || !answeredAt.isBefore(stored.getDeadlineAt())) return Optional.empty();
        List<PlacementAnswerVo> answers = new ArrayList<>(stored.getAnswers());
        answers.removeIf(answer -> answer.getQuestionId().equals(questionId));
        if (selectedOptionId != null) {
            answers.add(new PlacementAnswerVo(questionId, selectedOptionId, answeredAt));
        }
        Criteria criteria = Criteria.where("_id").is(id)
                .and("revision").is(expectedRevision)
                .and("status").is(PlacementAttemptStatus.IN_PROGRESS)
                .and("deadline_at").gt(answeredAt);
        Update update = new Update().set("answers", answers).set("updated_at", answeredAt).inc("revision", 1);
        if (mongo.updateFirst(Query.query(criteria), update, PlacementAttempt.class).getModifiedCount() != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findById(id, PlacementAttempt.class));
    }

    @Override
    public Optional<PlacementAttempt> finish(String id, long expectedRevision, PlacementAttemptStatus status,
                                             Instant endedAt, Integer correctCount, PlacementLevel level,
                                             Instant claimExpiresAt) {
        PlacementAttempt stored = mongo.findById(id, PlacementAttempt.class);
        if (stored == null || stored.getRevision() != expectedRevision
                || stored.getStatus() != PlacementAttemptStatus.IN_PROGRESS) return Optional.empty();
        if (status != PlacementAttemptStatus.EXPIRED && stored.getUserId() != null
                && findValidResultForUser(stored.getUserId()).isPresent()) return Optional.empty();
        Criteria criteria = Criteria.where("_id").is(id)
                .and("revision").is(expectedRevision)
                .and("status").is(PlacementAttemptStatus.IN_PROGRESS);
        Update update = new Update().set("status", status).set("ended_at", endedAt)
                .set("correct_count", correctCount).set("placement_level", level)
                .set("claim_expires_at", claimExpiresAt).set("updated_at", endedAt).inc("revision", 1);
        // Unclaimed Guest results retain the reservation until claim or claim expiry.
        if (stored.getUserId() != null || status == PlacementAttemptStatus.EXPIRED) {
            update.unset("start_session_key");
        }
        try {
            if (mongo.updateFirst(Query.query(criteria), update, PlacementAttempt.class).getModifiedCount() != 1) {
                return Optional.empty();
            }
            return Optional.ofNullable(mongo.findById(id, PlacementAttempt.class));
        } catch (DuplicateKeyException ignored) {
            return Optional.empty();
        }
    }

    private Optional<PlacementAttempt> findStartBlocker(PlacementAttempt proposed, String guestSessionId, Instant now) {
        Optional<PlacementAttempt> existing = guestSessionId == null ? Optional.empty()
                : findActiveForGuest(guestSessionId);
        String userId = proposed.getUserId();
        if (existing.isEmpty() && userId != null) existing = findValidResultForUser(userId);
        if (existing.isEmpty() && userId != null) existing = findActiveForUser(userId);
        if (existing.isEmpty() && guestSessionId != null) existing = findClaimableForGuest(guestSessionId, now);
        if (existing.isEmpty() && guestSessionId != null) {
            existing = Optional.ofNullable(mongo.findOne(Query.query(
                    Criteria.where("start_session_key").is(guestSessionId)), PlacementAttempt.class));
        }
        return existing;
    }

    private void releaseExpiredSession(String guestSessionId, Instant now) {
        if (guestSessionId == null) return;
        Criteria criteria = Criteria.where("start_session_key").is(guestSessionId)
                .and("user_id").is(null)
                .and("status").in(PlacementAttemptStatus.SUBMITTED, PlacementAttemptStatus.AUTO_SUBMITTED)
                .and("claim_expires_at").lte(now);
        mongo.updateFirst(Query.query(criteria), new Update().unset("start_session_key")
                .set("updated_at", now).inc("revision", 1), PlacementAttempt.class);
    }

    public void verifySessionReservationCompatibility(Instant now) {
        Criteria blocking = new Criteria().orOperator(
                Criteria.where("status").is(PlacementAttemptStatus.IN_PROGRESS),
                validResultCriteria().and("claim_expires_at").gt(now));
        Criteria legacy = Criteria.where("user_id").is(null).and("start_session_key").is(null)
                .andOperator(blocking);
        if (mongo.exists(Query.query(legacy), PlacementAttempt.class)) {
            throw new IllegalStateException("Placement blocking Guest attempts require explicit migration "
                    + "of start_session_key before Mongo mode can start; no data was backfilled");
        }
    }

    private Optional<PlacementAttempt> findLatest(Criteria criteria) {
        List<PlacementAttempt> attempts = mongo.find(Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "started_at")).limit(1), PlacementAttempt.class);
        return attempts.stream().findFirst();
    }

    private static Criteria validResultCriteria() {
        return Criteria.where("status").in(PlacementAttemptStatus.SUBMITTED, PlacementAttemptStatus.AUTO_SUBMITTED)
                .and("correct_count").ne(null)
                .and("placement_level").ne(null);
    }
}
