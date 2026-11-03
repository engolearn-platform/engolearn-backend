package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.speaking.dto.response.SpeakingAudioResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingItemConfigResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingSessionResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingSocketTicketResponse;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.model.*;
import fit.iuh.engolearn.speaking.repository.*;
import fit.iuh.engolearn.topic.contract.SpeakingItemLookup;
import fit.iuh.engolearn.topic.contract.dto.SpeakingItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SpeakingSessionService {
    private static final ZoneId LEARNER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<SpeakingSession.Status> OPEN = List.of(
            SpeakingSession.Status.ACTIVE, SpeakingSession.Status.INTERRUPTED);

    private final CurrentUserProvider users;
    private final SpeakingItemLookup items;
    private final SpeakingItemConfigRepository configs;
    private final SpeakingSessionRepository sessions;
    private final SpeakingTurnRepository turns;
    private final SpeakingAssessmentRepository assessments;
    private final SpeakingAudioAssetRepository audio;
    private final SpeakingUsageSegmentRepository usage;
    private final MediaStorage media;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    @Value("${engolearn.speaking.recovery.enabled:true}")
    private boolean recoveryEnabled = true;
    @Value("${engolearn.speaking.demo.enabled:false}")
    private boolean demoEnabled;
    @Autowired(required = false)
    private ApplicationEventPublisher events;

    public record SessionEndedEvent(String sessionId, String learnerId) {}

    private record Ticket(String learnerId, String sessionId, Instant expiresAt) {}

    public SpeakingSessionService(CurrentUserProvider users, SpeakingItemLookup items,
            SpeakingItemConfigRepository configs, SpeakingSessionRepository sessions,
            SpeakingTurnRepository turns, SpeakingAssessmentRepository assessments,
            SpeakingAudioAssetRepository audio, SpeakingUsageSegmentRepository usage,
            MediaStorage media) {
        this.users = users;
        this.items = items;
        this.configs = configs;
        this.sessions = sessions;
        this.turns = turns;
        this.assessments = assessments;
        this.audio = audio;
        this.usage = usage;
        this.media = media;
    }

    public SpeakingItemConfigResponse config(String itemId) {
        String learner = learnerKey();
        SpeakingItemDto item = items.findOpenableItem(itemId, learner)
                .orElseThrow(() -> problem(HttpStatus.FORBIDDEN, "ITEM_UNAVAILABLE", "Item is not openable"));
        SpeakingItemConfig config = publishedConfig(item.itemVersionId());
        return new SpeakingItemConfigResponse(itemId, item.itemVersionId(), item.title(),
                config.getGoals(), config.getMinimumValidTurns());
    }

    public SpeakingSessionResponse create(String itemId, String requestId, boolean replace) {
        String learner = learnerKey();
        Optional<SpeakingSession> byRequest = sessions.findById(requestId);
        if (byRequest.isPresent()) {
            SpeakingSession previous = byRequest.get();
            if (learner.equals(previous.getLearnerId()) && previous.getContent().topicItemId().equals(itemId)
                    && previous.getDeletedAt() == null) return response(previous);
            throw problem(HttpStatus.CONFLICT, "REQUEST_ID_REUSED", "Request ID was already used");
        }
        Optional<SpeakingSession> existing = sessions.findFirstByLearnerIdAndStatusInAndDeletedAtIsNull(learner, OPEN);
        if (existing.isPresent() && requestId.equals(existing.get().getId())) return response(existing.get());
        SpeakingItemDto item = items.findOpenableItem(itemId, learner)
                .orElseThrow(() -> problem(HttpStatus.FORBIDDEN, "ITEM_UNAVAILABLE", "Item is not openable"));
        SpeakingItemConfig config = publishedConfig(item.itemVersionId());
        if (remainingMillis(learner, Instant.now()) <= 0) {
            throw problem(HttpStatus.TOO_MANY_REQUESTS, "DAILY_QUOTA", "Daily speaking time is exhausted");
        }
        if (existing.isPresent() && !replace) {
            throw problem(HttpStatus.CONFLICT, "SESSION_EXISTS", "An unfinished session already exists");
        }
        if (existing.isPresent()) {
            finish(existing.get(), SpeakingSession.EndReason.REPLACED);
            awaitTerminal(existing.get().getId());
        }
        Instant now = Instant.now();
        SpeakingSession session = SpeakingSession.builder().id(requestId).learnerId(learner)
                .openLearnerId(learner)
                .content(new SpeakingContentSnapshot(itemId, item.itemVersionId(), item.title(),
                        item.description(), List.copyOf(config.getGoals()), null,
                        config.getMinimumValidTurns(), List.copyOf(item.vocabulary()),
                        List.copyOf(item.expressions()), config.getRevision(), "REQUIRED_GOALS_V2"))
                .status(SpeakingSession.Status.ACTIVE).startedAt(now).expiresAt(now.plus(Duration.ofMinutes(20)))
                .achievedGoalIds(new ArrayList<>()).controllerEpoch(1).openingReply(
                        "Hello! Let's talk about " + item.title() + ".")
                .build();
        try {
            return response(sessions.save(session));
        } catch (DuplicateKeyException ex) {
            throw problem(HttpStatus.CONFLICT, "SESSION_EXISTS", "An unfinished session already exists");
        }
    }

    public SpeakingSessionResponse current() {
        String learner = learnerKey();
        return sessions.findFirstByLearnerIdAndStatusInAndDeletedAtIsNull(learner, OPEN)
                .map(this::expireIfNeeded).filter(SpeakingSession::isOpen).map(this::response).orElse(null);
    }

    public SpeakingSessionResponse get(String id) { return response(owned(id)); }

    public List<SpeakingSessionResponse> history(String itemId) {
        String learner = learnerKey();
        List<SpeakingSession> found = itemId == null || itemId.isBlank()
                ? sessions.findByLearnerIdAndDeletedAtIsNullOrderByStartedAtDesc(learner)
                : sessions.findByLearnerIdAndContentTopicItemIdAndDeletedAtIsNullOrderByStartedAtDesc(learner, itemId);
        return found.stream().map(this::response).toList();
    }

    public synchronized SpeakingSessionResponse resume(String id, String requestId) {
        SpeakingSession session = owned(id);
        if (requestId.equals(session.getLastResumeRequestId())) return response(session);
        expireIfNeeded(session);
        if (!session.isOpen()) throw problem(HttpStatus.CONFLICT, "SESSION_ENDED", "Session has ended");
        session.setStatus(SpeakingSession.Status.ACTIVE);
        session.setDisconnectedAt(null);
        session.setControllerEpoch(session.getControllerEpoch() + 1);
        session.setLastResumeRequestId(requestId);
        return response(sessions.save(session));
    }

    public synchronized void interruptIfDisconnected(String id, String learnerId, long epoch) {
        sessions.findById(id).ifPresent(session -> {
            if (session.getDeletedAt() == null && session.isOpen()
                    && session.getLearnerId().equals(learnerId)
                    && session.getControllerEpoch() == epoch
                    && session.getStatus() == SpeakingSession.Status.ACTIVE) {
                session.setStatus(SpeakingSession.Status.INTERRUPTED);
                session.setDisconnectedAt(Instant.now());
                sessions.save(session);
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        if (!recoveryEnabled) return;
        for (SpeakingSession session : sessions.findByStatusIn(OPEN)) {
            if (session.getDeletedAt() == null) finish(session, SpeakingSession.EndReason.SERVICE_RESTART);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void endExpiredSessions() {
        if (!recoveryEnabled) return;
        Instant now = Instant.now();
        for (SpeakingSession session : sessions.findByStatusIn(OPEN)) {
            if (session.getDeletedAt() == null && !now.isBefore(session.getExpiresAt())) {
                finish(session, SpeakingSession.EndReason.SESSION_AGE);
            }
        }
    }

    public SpeakingSessionResponse end(String id, long epoch, String requestId) {
        SpeakingSession session = owned(id);
        synchronized (this) {
            if (session.isOpen()) {
                requireEpoch(session, epoch);
                if (session.getClosingAt() == null) session.setEndRequestId(requestId);
                finish(session, SpeakingSession.EndReason.LEARNER_ENDED);
            }
        }
        if (session.isOpen()) awaitTerminal(id);
        return response(sessions.findById(id).orElseThrow());
    }

    public SpeakingSessionResponse end(String id, long epoch) {
        return end(id, epoch, UUID.randomUUID().toString());
    }

    public SpeakingSessionResponse delete(String id) {
        SpeakingSession session = owned(id);
        if (session.isOpen()) {
            finish(session, SpeakingSession.EndReason.SOFT_DELETED);
            awaitTerminal(id);
            session = sessions.findById(id).orElseThrow();
        }
        session.setDeletedAt(Instant.now());
        sessions.save(session);
        return response(session);
    }

    public int deleteAll() {
        List<SpeakingSession> found = sessions.findByLearnerIdAndDeletedAtIsNullOrderByStartedAtDesc(
                learnerKey());
        found.forEach(session -> {
            if (session.isOpen()) {
                finish(session, SpeakingSession.EndReason.SOFT_DELETED);
                awaitTerminal(session.getId());
            }
            SpeakingSession closed = sessions.findById(session.getId()).orElseThrow();
            closed.setDeletedAt(Instant.now());
            sessions.save(closed);
        });
        return found.size();
    }

    public SpeakingAudioResponse playback(String sessionId, String turnId, SpeakingAudioAsset.Kind kind) {
        SpeakingSession session = owned(sessionId);
        if (turns.findByIdAndSessionId(turnId, sessionId).isEmpty()) {
            throw problem(HttpStatus.NOT_FOUND, "TURN_NOT_FOUND", "Turn not found");
        }
        SpeakingAudioAsset asset = audio.findByTurnId(turnId).stream().filter(a -> a.getKind() == kind)
                .findFirst().orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "AUDIO_NOT_FOUND", "Audio not found"));
        if (kind == SpeakingAudioAsset.Kind.LEARNER_RECORDING
                && !Instant.now().isBefore(asset.getPlaybackUntil())) {
            throw problem(HttpStatus.GONE, "AUDIO_EXPIRED", "Recording playback has expired");
        }
        return new SpeakingAudioResponse(media.privateAudioUrl(asset.getCloudinaryPublicId(),
                asset.getFormat(), Duration.ofSeconds(60)), 60);
    }

    public SpeakingSocketTicketResponse ticket(String sessionId, long epoch) {
        SpeakingSession session = owned(sessionId);
        requireEpoch(session, epoch);
        if (!session.isOpen()) throw problem(HttpStatus.CONFLICT, "SESSION_ENDED", "Session has ended");
        String token = UUID.randomUUID().toString();
        tickets.put(token, new Ticket(session.getLearnerId(), sessionId, Instant.now().plusSeconds(30)));
        return new SpeakingSocketTicketResponse(token, 30);
    }

    public SpeakingSession consumeTicket(String token, String sessionId) {
        Ticket ticket = tickets.remove(token);
        if (ticket == null || !ticket.sessionId().equals(sessionId)
                || Instant.now().isAfter(ticket.expiresAt())) {
            throw problem(HttpStatus.UNAUTHORIZED, "INVALID_TICKET", "Socket ticket is invalid or expired");
        }
        SpeakingSession session = sessions.findById(sessionId).orElseThrow(() ->
                problem(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        if (!session.getLearnerId().equals(ticket.learnerId()) || session.getDeletedAt() != null || !session.isOpen()) {
            throw problem(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "Session is not available");
        }
        return session;
    }

    public SpeakingSessionResponse socketSnapshot(String id, String learnerId) {
        SpeakingSession session = sessions.findById(id).orElseThrow(() ->
                problem(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        if (!learnerId.equals(session.getLearnerId()) || session.getDeletedAt() != null) {
            throw problem(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "Session is not available");
        }
        return response(expireIfNeeded(session));
    }

    public SpeakingSession demoState(String id, String learnerId) {
        SpeakingSession session = sessions.findById(id).orElseThrow(() ->
                problem(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        if (!learnerId.equals(session.getLearnerId()) || session.getDeletedAt() != null) {
            throw problem(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "Session is not available");
        }
        return expireIfNeeded(session);
    }

    public SpeakingSession requireController(String id, long epoch) {
        SpeakingSession session = owned(id);
        return checkController(session, epoch);
    }

    public SpeakingSession requireSocketController(String id, String learnerId, long epoch) {
        SpeakingSession session = sessions.findById(id).orElseThrow(() ->
                problem(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        if (!learnerId.equals(session.getLearnerId()) || session.getDeletedAt() != null) {
            throw problem(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "Session is not available");
        }
        return checkController(session, epoch);
    }

    private SpeakingSession checkController(SpeakingSession session, long epoch) {
        expireIfNeeded(session);
        requireEpoch(session, epoch);
        if (!session.isOpen() || session.getClosingAt() != null) {
            throw problem(HttpStatus.CONFLICT, "SESSION_ENDED", "Session cannot accept a turn");
        }
        if (session.getCommittedTurnCount() >= 30) {
            finish(session, SpeakingSession.EndReason.TURN_LIMIT);
            throw problem(HttpStatus.CONFLICT, "TURN_LIMIT", "Turn limit reached");
        }
        if (remainingMillis(session.getLearnerId(), Instant.now()) <= 0) {
            finish(session, SpeakingSession.EndReason.DAILY_QUOTA);
            throw problem(HttpStatus.TOO_MANY_REQUESTS, "DAILY_QUOTA", "Daily speaking time is exhausted");
        }
        return session;
    }

    public synchronized SpeakingTurn commit(String sessionId, String learnerId, long epoch,
                                             String turnId, String transcript) {
        SpeakingSession session = requireSocketController(sessionId, learnerId, epoch);
        Optional<SpeakingTurn> duplicate = turns.findById(turnId);
        if (duplicate.isPresent()) {
            if (!duplicate.get().getSessionId().equals(sessionId)) {
                throw problem(HttpStatus.CONFLICT, "TURN_ID_REUSED", "Turn ID belongs to another session");
            }
            return duplicate.get();
        }
        if (turns.findBySessionIdOrderBySequenceAsc(sessionId).stream()
                .anyMatch(t -> t.getEvaluationState() == SpeakingTurn.EvaluationState.PENDING)) {
            throw problem(HttpStatus.CONFLICT, "TURN_BUSY", "Previous turn is still being assessed");
        }
        SpeakingTurn turn = SpeakingTurn.builder().id(turnId).sessionId(sessionId)
                .sequence(session.getCommittedTurnCount() + 1).transcript(transcript)
                .committedAt(Instant.now()).evaluationState(SpeakingTurn.EvaluationState.PENDING).build();
        turns.save(turn);
        session.setCommittedTurnCount(turn.getSequence());
        sessions.save(session);
        if (session.getCommittedTurnCount() >= 30) {
            finish(session, SpeakingSession.EndReason.TURN_LIMIT);
        }
        return turn;
    }

    public synchronized void assessed(String sessionId, String turnId,
            fit.iuh.engolearn.speaking.utils.port.ConversationEvaluator.Result result) {
        SpeakingSession session = sessions.findById(sessionId).orElseThrow();
        SpeakingTurn turn = turns.findByIdAndSessionId(turnId, sessionId).orElseThrow();
        if (session.getDeletedAt() != null || !session.isOpen()
                || turn.getEvaluationState() != SpeakingTurn.EvaluationState.PENDING) return;
        if (session.getClosingAt() != null
                && !Instant.now().isBefore(session.getClosingAt().plusSeconds(10))) return;
        turn.setValid(result.valid());
        turn.setAiReply(result.reply());
        turn.setEvaluationState(result.valid() ? SpeakingTurn.EvaluationState.SUCCEEDED
                : SpeakingTurn.EvaluationState.INVALID);
        if (result.valid()) {
            SpeakingAssessmentRecord assessment = SpeakingAssessmentRecord.builder().id(turnId)
                    .sessionId(sessionId).learnerId(session.getLearnerId()).transcript(turn.getTranscript())
                    .criteriaVersion("speaking-v1").goalEvidence(result.goalEvidence())
                    .resourceEvidence(result.resourceEvidence()).corrections(result.corrections())
                    .hint(result.hint()).feedback(result.feedback()).build();
            assessments.save(assessment);
            session.setValidTurnCount(session.getValidTurnCount() + 1);
            Set<String> achieved = new LinkedHashSet<>(session.getAchievedGoalIds());
            result.goalEvidence().forEach(e -> achieved.add(e.goalId()));
            session.setAchievedGoalIds(new ArrayList<>(achieved));
            session.setConsecutiveEvaluationFailures(0);
        }
        turns.save(turn);
        sessions.save(session);
        if (session.getClosingAt() != null) {
            finish(session, session.getClosingReason());
        } else {
            if (session.getContent().legacyRatioPolicy() ? complete(session) : allGoalsAndTurns(session)) {
                finish(session, SpeakingSession.EndReason.GOALS_REACHED);
            }
            else if (session.getCommittedTurnCount() >= 30) finish(session, SpeakingSession.EndReason.TURN_LIMIT);
        }
    }

    public synchronized boolean settleDemo(String sessionId, String turnId, long controllerEpoch,
            SpeakingDemoAssessment assessment) {
        SpeakingSession session = sessions.findById(sessionId).orElseThrow();
        expireIfNeeded(session);
        Optional<SpeakingTurn> found = turns.findByIdAndSessionId(turnId, sessionId);
        if (found.isEmpty()) return false;
        SpeakingTurn turn = found.get();
        if (session.getDeletedAt() != null || !session.isOpen() || session.getClosingAt() != null
                || session.getControllerEpoch() != controllerEpoch
                || turn.getEvaluationState() != SpeakingTurn.EvaluationState.PENDING) return false;

        DialogueDecision decision = assessment.decision();
        if (decision.finalizedTurnCount() != session.getCommittedTurnCount()) return false;
        ValidatedTurnProposal proposal = assessment.proposal();
        if (proposal.validityOutcome() == ValidatedTurnProposal.ValidityOutcome.VALID) {
            if (decision.validTurnCount() != session.getValidTurnCount() + 1) return false;
            turn.setValid(true);
            turn.setAiReply(decision.spokenReplyToDeliver());
            turn.setEvaluationState(SpeakingTurn.EvaluationState.SUCCEEDED);
            SpeakingAssessmentRecord assessmentRecord = SpeakingAssessmentRecordAdapter.toRecord(sessionId,
                    session.getLearnerId(), turn.getTranscript(), proposal);
            assessmentRecord.setId(turnId);
            assessments.save(assessmentRecord);
            session.setValidTurnCount(decision.validTurnCount());
            Set<String> achieved = new LinkedHashSet<>(session.getAchievedGoalIds() == null
                    ? List.of() : session.getAchievedGoalIds());
            achieved.addAll(decision.newlyAchievedGoalIds());
            session.setAchievedGoalIds(new ArrayList<>(achieved));
            session.setConsecutiveEvaluationFailures(decision.consecutiveEvaluationErrors());
        } else if (proposal.validityOutcome() == ValidatedTurnProposal.ValidityOutcome.INVALID) {
            turn.setValid(false);
            turn.setAiReply(null);
            turn.setEvaluationState(SpeakingTurn.EvaluationState.INVALID);
            session.setConsecutiveEvaluationFailures(decision.consecutiveEvaluationErrors());
        } else {
            turn.setValid(false);
            turn.setAiReply(null);
            turn.setEvaluationState(SpeakingTurn.EvaluationState.FAILED);
            session.setConsecutiveEvaluationFailures(decision.consecutiveEvaluationErrors());
        }
        turns.save(turn);
        sessions.save(session);
        settleDemoLifecycle(session, decision);
        return true;
    }

    private void settleDemoLifecycle(SpeakingSession session, DialogueDecision decision) {
        if (decision.resultingStatus() != SpeakingSession.Status.COMPLETED
                && decision.resultingStatus() != SpeakingSession.Status.INCOMPLETE
                && decision.outcome() != DialogueDecision.Outcome.TERMINAL) return;
        SpeakingSession.EndReason reason = switch (decision.reasonCode()) {
            case EVALUATION_ERRORS_3 -> SpeakingSession.EndReason.EVALUATION_FAILURES;
            case AUTO_ALL_GOALS_AND_MIN_TURNS, TARGET_ALREADY_ACHIEVED -> SpeakingSession.EndReason.GOALS_REACHED;
            case FINALIZED_TURN_LIMIT -> SpeakingSession.EndReason.TURN_LIMIT;
            case SESSION_AGE_LIMIT -> SpeakingSession.EndReason.SESSION_AGE;
            case DAILY_QUOTA_EXHAUSTED -> SpeakingSession.EndReason.DAILY_QUOTA;
            case USER_END_ACTION -> SpeakingSession.EndReason.LEARNER_ENDED;
            case RECOVERY_EXPIRED -> SpeakingSession.EndReason.RECOVERY_EXPIRED;
            case SERVICE_RESTART -> SpeakingSession.EndReason.SERVICE_RESTART;
            default -> SpeakingSession.EndReason.GOALS_REACHED;
        };
        finish(session, reason);
    }

    public synchronized boolean recoverDemoTurn(String sessionId, String turnId, long controllerEpoch) {
        SpeakingSession session = sessions.findById(sessionId).orElseThrow();
        expireIfNeeded(session);
        Optional<SpeakingTurn> found = turns.findByIdAndSessionId(turnId, sessionId);
        if (found.isEmpty()) return false;
        SpeakingTurn turn = found.get();
        if (session.getDeletedAt() != null || !session.isOpen()
                || session.getControllerEpoch() != controllerEpoch
                || turn.getEvaluationState() != SpeakingTurn.EvaluationState.PENDING) return false;
        turn.setValid(false);
        turn.setAiReply(null);
        turn.setEvaluationState(SpeakingTurn.EvaluationState.FAILED);
        turns.save(turn);
        sessions.save(session);
        if (session.getClosingAt() != null) finish(session, session.getClosingReason());
        return true;
    }

    public synchronized void assessmentFailed(String sessionId, String turnId, long controllerEpoch) {
        SpeakingSession session = sessions.findById(sessionId).orElseThrow();
        SpeakingTurn turn = turns.findByIdAndSessionId(turnId, sessionId).orElseThrow();
        if (!session.isOpen() || session.getDeletedAt() != null
                || session.getControllerEpoch() != controllerEpoch
                || turn.getEvaluationState() != SpeakingTurn.EvaluationState.PENDING) return;
        if (session.getClosingAt() != null
                && !Instant.now().isBefore(session.getClosingAt().plusSeconds(10))) return;
        turn.setEvaluationState(SpeakingTurn.EvaluationState.FAILED);
        turns.save(turn);
        session.setConsecutiveEvaluationFailures(session.getConsecutiveEvaluationFailures() + 1);
        sessions.save(session);
        if (session.getClosingAt() != null) {
            finish(session, session.getClosingReason());
        } else if (session.getConsecutiveEvaluationFailures() >= 3) {
            finish(session, SpeakingSession.EndReason.EVALUATION_FAILURES);
        }
    }

    public void saveAudio(String sessionId, String turnId, SpeakingAudioAsset.Kind kind,
                          String format, String contentType, byte[] bytes) {
        SpeakingSession session = sessions.findById(sessionId).orElseThrow();
        if (session.getDeletedAt() != null || bytes.length == 0) return;
        String publicId = "speaking/" + sessionId + "/" + turnId + "/" + kind.name().toLowerCase();
        MediaStorage.StoredAudio stored = media.uploadPrivateAudio(publicId, format, bytes);
        Instant now = Instant.now();
        audio.save(SpeakingAudioAsset.builder().sessionId(sessionId).turnId(turnId).kind(kind)
                .cloudinaryPublicId(stored.publicId()).format(stored.format()).contentType(contentType)
                .recordedAt(now).playbackUntil(now.plus(Duration.ofDays(30))).build());
    }

    public void recordUsage(String sessionId, String turnId, SpeakingUsageSegment.Kind kind,
                            Instant started, Instant ended) {
        SpeakingSession session = sessions.findById(sessionId).orElseThrow();
        Instant cursor = started;
        int segment = 0;
        while (cursor.isBefore(ended)) {
            LocalDate day = cursor.atZone(LEARNER_ZONE).toLocalDate();
            Instant boundary = day.plusDays(1).atStartOfDay(LEARNER_ZONE).toInstant();
            Instant stop = ended.isBefore(boundary) ? ended : boundary;
            usage.save(SpeakingUsageSegment.builder().id(turnId + ":" + kind + ":" + segment++)
                    .learnerId(session.getLearnerId()).sessionId(sessionId).turnId(turnId)
                    .day(day).kind(kind).startedAt(cursor).endedAt(stop)
                    .durationMillis(Duration.between(cursor, stop).toMillis()).build());
            cursor = stop;
        }
    }

    public long remainingMillis(String learner, Instant now) {
        LocalDate day = now.atZone(LEARNER_ZONE).toLocalDate();
        long used = usage.findByLearnerIdAndDay(learner, day).stream()
                .mapToLong(SpeakingUsageSegment::getDurationMillis).sum();
        return Math.max(0, Duration.ofMinutes(60).toMillis() - used);
    }

    private SpeakingItemConfig publishedConfig(String versionId) {
        SpeakingItemConfig config = configs.findByTopicItemVersionId(versionId).orElseThrow(() ->
                problem(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_MISSING", "Speaking is not configured for this item"));
        if (config.getStatus() != SpeakingItemConfig.Status.PUBLISHED || config.getGoals() == null
                || config.getGoals().size() < 3 || config.getGoals().size() > 5
                || config.getMinimumValidTurns() < 3 || config.getMinimumValidTurns() > 5
                || config.getGoals().stream().noneMatch(goal -> goal.priority() == SpeakingGoal.Priority.REQUIRED)
                || config.getGoals().stream().anyMatch(goal -> goal.id() == null || goal.id().isBlank()
                    || goal.title() == null || goal.title().isBlank() || goal.priority() == null)
                || config.getGoals().stream().map(SpeakingGoal::id).distinct().count() != config.getGoals().size()) {
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_INVALID", "Speaking configuration is invalid");
        }
        return config;
    }

    private SpeakingSession owned(String id) {
        String learner = learnerKey();
        SpeakingSession session = sessions.findById(id).orElseThrow(() ->
                problem(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        if (!learner.equals(session.getLearnerId()) || session.getDeletedAt() != null) {
            throw problem(HttpStatus.FORBIDDEN, "SESSION_FORBIDDEN", "Session is not available");
        }
        return session;
    }

    private SpeakingSession expireIfNeeded(SpeakingSession session) {
        if (session.isOpen() && !Instant.now().isBefore(session.getExpiresAt())) {
            finish(session, SpeakingSession.EndReason.SESSION_AGE);
        }
        return session;
    }

    private void requireEpoch(SpeakingSession session, long epoch) {
        if (epoch != session.getControllerEpoch()) {
            throw problem(HttpStatus.CONFLICT, "CONTROLLER_CHANGED", "Another tab controls this session");
        }
    }

    private boolean complete(SpeakingSession session) {
        return ConversationStateView.from(session).completionFor(session.getContent()).completionEligible();
    }

    private boolean allGoalsAndTurns(SpeakingSession session) {
        return ConversationStateView.from(session).completionFor(session.getContent()).autoEndEligible();
    }

    private synchronized void finish(SpeakingSession session, SpeakingSession.EndReason reason) {
        if (!session.isOpen()) return;
        if (session.getClosingAt() == null) {
            session.setClosingAt(Instant.now());
            session.setClosingReason(reason);
            sessions.save(session);
            String id = session.getId();
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
                synchronized (this) {
                    sessions.findById(id).ifPresent(closing -> finalizeClosing(closing));
                }
            });
        }
        finalizeClosing(session);
    }

    private void finalizeClosing(SpeakingSession session) {
        if (!session.isOpen() || session.getClosingAt() == null) return;
        boolean deadlineReached = !Instant.now().isBefore(session.getClosingAt().plusSeconds(10));
        boolean pending = turns.findBySessionIdOrderBySequenceAsc(session.getId()).stream()
                .anyMatch(turn -> turn.getEvaluationState() == SpeakingTurn.EvaluationState.PENDING);
        if (pending && !deadlineReached) return;
        session.setStatus(complete(session) ? SpeakingSession.Status.COMPLETED
                : SpeakingSession.Status.INCOMPLETE);
        session.setOpenLearnerId(null);
        session.setEndReason(session.getClosingReason());
        session.setEndedAt(Instant.now());
        sessions.save(session);
        if (events != null) events.publishEvent(new SessionEndedEvent(session.getId(), session.getLearnerId()));
    }

    private void awaitTerminal(String id) {
        while (true) {
            SpeakingSession session = sessions.findById(id).orElseThrow();
            if (!session.isOpen()) return;
            synchronized (this) { finalizeClosing(session); }
            if (!session.isOpen()) return;
            try { Thread.sleep(50); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw problem(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_CLOSING", "Session is still closing");
            }
        }
    }

    private SpeakingSessionResponse response(SpeakingSession session) {
        List<SpeakingSessionResponse.Turn> result = turns.findBySessionIdOrderBySequenceAsc(session.getId())
                .stream().map(turn -> {
                    List<SpeakingAudioAsset> assets = audio.findByTurnId(turn.getId());
                    boolean recording = assets.stream().anyMatch(a ->
                            a.getKind() == SpeakingAudioAsset.Kind.LEARNER_RECORDING
                                    && Instant.now().isBefore(a.getPlaybackUntil()));
                    boolean reply = assets.stream().anyMatch(a -> a.getKind() == SpeakingAudioAsset.Kind.AI_REPLY);
                    SpeakingAssessmentRecord record = assessments.findById(turn.getId()).orElse(null);
                    SpeakingSessionResponse.Assessment assessment = record == null ? null
                            : new SpeakingSessionResponse.Assessment(record.getId(), record.getTranscript(),
                            record.getCriteriaVersion(), record.getGoalEvidence(), record.getResourceEvidence(),
                            record.getCorrections(), record.getHint(), record.getFeedback());
                    return new SpeakingSessionResponse.Turn(turn.getId(), turn.getSequence(),
                            turn.getTranscript(), turn.getCommittedAt(), turn.isValid(),
                            turn.getEvaluationState(), turn.getAiReply(), assessment, recording, reply);
                }).toList();
        Map<String, String> quotes = new LinkedHashMap<>();
        for (SpeakingSessionResponse.Turn turn : result) {
            if (turn.assessment() == null || turn.assessment().goalEvidence() == null) continue;
            for (SpeakingAssessmentRecord.GoalEvidence evidence : turn.assessment().goalEvidence()) {
                quotes.putIfAbsent(evidence.goalId(), evidence.quote());
            }
        }
        Set<String> achieved = new HashSet<>(session.getAchievedGoalIds());
        List<SpeakingSessionResponse.GoalResult> goalResults = session.getContent().goals().stream()
                .map(goal -> new SpeakingSessionResponse.GoalResult(goal.id(), goal.priority(),
                        achieved.contains(goal.id()), quotes.get(goal.id())))
                .toList();
        boolean requiredGoalsMet = session.getContent().legacyRatioPolicy()
                ? achieved.size() >= session.getContent().requiredGoalCount()
                : session.getContent().goals().stream()
                    .filter(goal -> goal.priority() == SpeakingGoal.Priority.REQUIRED)
                    .allMatch(goal -> achieved.contains(goal.id()));
        return new SpeakingSessionResponse(session.getId(), session.getContent(), session.getStatus(),
                session.getEndReason(), session.getStartedAt(), session.getExpiresAt(), session.getEndedAt(),
                session.getValidTurnCount(), session.getCommittedTurnCount(), session.getAchievedGoalIds(),
                goalResults, requiredGoalsMet, complete(session),
                session.getControllerEpoch(), session.getRevision(),
                session.getStatus() == SpeakingSession.Status.ACTIVE && session.getClosingAt() == null
                        && session.getCommittedTurnCount() < 30 && Instant.now().isBefore(session.getExpiresAt())
                        && result.stream().noneMatch(t -> t.evaluationState() == SpeakingTurn.EvaluationState.PENDING),
                session.getOpeningReply(), result);
    }

    private SpeakingException problem(HttpStatus status, String code, String message) {
        return new SpeakingException(status, code, message);
    }

    private String learnerKey() {
        if (!demoEnabled) return users.requireCurrentUserId();
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw problem(HttpStatus.BAD_REQUEST, "DEMO_KEY_REQUIRED", "Demo learner key is required");
        }
        String key = attributes.getRequest().getHeader("X-Speaking-Demo-Learner-Key");
        if (key == null || key.isBlank() || key.length() > 128) {
            throw problem(HttpStatus.BAD_REQUEST, "DEMO_KEY_REQUIRED", "Demo learner key is required");
        }
        return key.trim();
    }
}
