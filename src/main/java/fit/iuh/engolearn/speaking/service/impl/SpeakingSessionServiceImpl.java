package fit.iuh.engolearn.speaking.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.quota.contract.QuotaAvailability;
import fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException;
import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.speaking.dto.request.SpeakingRequests.*;
import fit.iuh.engolearn.speaking.dto.response.SpeakingAssessmentResponse;
import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.*;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.mapper.SpeakingSessionMapper;
import fit.iuh.engolearn.speaking.model.*;
import fit.iuh.engolearn.speaking.model.enums.*;
import fit.iuh.engolearn.speaking.model.vo.*;
import fit.iuh.engolearn.speaking.repository.*;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.speaking.service.SpeakingSessions;
import fit.iuh.engolearn.speaking.utils.SpeakingSessionSettings;
import fit.iuh.engolearn.topic.contract.TopicSpeakingAccess;
import fit.iuh.engolearn.topic.contract.dto.SpeakingScopeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SpeakingSessionServiceImpl implements SpeakingSessions, SpeakingRuntime {
    private static final String QUOTA_TYPE = "SPEAKING_DURATION";
    private static final int ATTEMPTS = 12;
    private static final Sort HISTORY_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final SpeakingSessionRepository sessions;
    private final SpeakingOwnerSlotRepository owners;
    private final SpeakingTurnRepository turns;
    private final SpeakingTurnArchive archive;
    private final TopicSpeakingAccess topicAccess;
    private final QuotaAvailability quota;
    private final MediaManagement media;
    private final CurrentUserProvider users;
    private final SpeakingSessionSettings settings;
    private final Clock clock;
    private final String instance = id();

    @Override
    @PreAuthorize("hasRole('USER')")
    public Availability availability(SpeakingScopeDto scope) {
        String owner = users.requireCurrentUserId();
        validateScope(scope);
        var access = topicAccess.resolve(owner, scope);
        String reason = access.reason() == null ? null : access.reason().name();
        if (reason == null && !hasQuota(owner)) reason = "DAILY_QUOTA_REACHED";
        return new Availability(scope, reason == null, reason,
                SpeakingSessionMapper.current(sessions.findByUnfinishedOwner(owner).orElse(null)));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public StartResult create(Create request) {
        String owner = users.requireCurrentUserId();
        validateScope(request.scope());
        String requestId = request.clientRequestId().toString();
        StartResult prior = replay(owner, requestId, request.scope(), null, null);
        if (prior != null) return prior;
        SpeakingStartVo intent = newStart(owner, request.scope(), requestId, null, null);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            SpeakingOwnerSlot slot = slot(owner);
            if (slot.getStart() != null) {
                SpeakingSession current = reconcile(slot);
                prior = replay(owner, requestId, request.scope(), null, null);
                if (prior != null) return prior;
                if (current != null) {
                    throw new SpeakingException(HttpStatus.CONFLICT, "ACTIVE_SESSION_EXISTS",
                            "An unfinished Speaking session already exists", false, current.getId());
                }
                continue;
            }
            slot.setStart(intent);
            if (saveSlot(slot)) return new StartResult(view(materialize(owner, intent)), true, null);
        }
        throw busy();
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public StartResult replace(Replace request) {
        String owner = users.requireCurrentUserId();
        validateScope(request.scope());
        String requestId = request.clientRequestId().toString();
        var prior = replay(owner, requestId, request.scope(), request.sessionId(), request.controllerEpoch());
        if (prior != null) return prior;

        SpeakingSession old = owned(request.sessionId(), owner);
        requireEpoch(old, request.controllerEpoch());
        SpeakingStartVo intent = old.getReplacement() == null
                ? newStart(owner, request.scope(), requestId, old.getId(), request.controllerEpoch()) : null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            if (old.getReplacement() != null) {
                var existing = old.getReplacement();
                if (!existing.getClientRequestId().equals(requestId)) throw closing();
                if (!request.scope().equals(SpeakingSessionMapper.scope(existing.getSnapshot()))) throw requestConflict();
                return replacementResult(old, false);
            }
            requireControlled(old);
            if (intent == null) throw busy();
            old.setReplacement(intent);
            beginClosing(old, SpeakingEndReason.REPLACED);
            SpeakingSession saved = trySave(old);
            if (saved != null) return replacementResult(saved, true);
            old = owned(request.sessionId(), owner);
            requireEpoch(old, request.controllerEpoch());
        }
        throw busy();
    }

    private StartResult replacementResult(SpeakingSession old, boolean isNew) {
        SpeakingSession latest = advance(old.getId());
        if (latest.getDeletedAt() != null) throw missing();
        if (latest.getStatus() != SpeakingSessionStatus.ENDED) return new StartResult(null, false, old.getId());
        finishSlot(latest);
        reconcile(slot(old.getOwnerId()));
        var child = sessions.findById(old.getReplacement().getSessionId()).orElseThrow(SpeakingSessionServiceImpl::busy);
        if (child.getDeletedAt() != null) throw missing();
        return new StartResult(view(child), isNew, null);
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Session read(String id) {
        return view(owned(id, users.requireCurrentUserId()));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Session resume(String id, Resume request) {
        String owner = users.requireCurrentUserId();
        String requestId = request.clientRequestId().toString();
        return view(change(id, session -> {
            requireOwner(session, owner);
            requireControlled(session);
            if (requestId.equals(session.getResumeRequestId())) {
                if (session.getControllerEpoch() != request.controllerEpoch() + 1) throw requestConflict();
                return false;
            }
            requireEpoch(session, request.controllerEpoch());
            discardReceiving(session, "CONTROLLER_CHANGED");
            session.setControllerEpoch(session.getControllerEpoch() + 1);
            session.setResumeRequestId(requestId);
            clearConnection(session);
            return true;
        }));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Ticket ticket(String id, long epoch) {
        String owner = users.requireCurrentUserId();
        String token = id() + id();
        change(id, session -> {
            requireOwner(session, owner);
            requireEpoch(session, epoch);
            if (session.getStatus() == SpeakingSessionStatus.ENDED) throw ended();
            if (session.getLeaseId() != null && instance.equals(session.getLeaseInstance())) {
                throw failure(HttpStatus.CONFLICT, "CONTROLLER_CHANGED", "A controller is already connected", true);
            }
            if (!instance.equals(session.getLeaseInstance())) {
                discardReceiving(session, "SYSTEM_INTERRUPTED");
                clearConnection(session);
            }
            session.setTicketHash(hash(token));
            session.setTicketExpiresAt(now().plus(SpeakingSessionSettings.TICKET_LIFETIME));
            return true;
        });
        return new Ticket(token, SpeakingSessionSettings.TICKET_LIFETIME.toSeconds());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Session end(String id, long epoch) {
        String owner = users.requireCurrentUserId();
        change(id, current -> {
            requireOwner(current, owner);
            requireEpoch(current, epoch);
            if (current.getStatus() == SpeakingSessionStatus.ENDED || current.getStatus() == SpeakingSessionStatus.CLOSING) return false;
            beginClosing(current, SpeakingEndReason.USER_ENDED);
            return true;
        });
        SpeakingSession latest = advance(id);
        if (latest.getDeletedAt() != null) throw missing();
        if (latest.getStatus() == SpeakingSessionStatus.ENDED) finishSlot(latest);
        return view(latest);
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Page<Session> history(SpeakingScopeDto scope, int page, int size) {
        var pageable = page(page, size);
        String owner = users.requireCurrentUserId();
        if (scope != null) validateScope(scope);
        var rows = scope == null ? sessions.findByOwnerIdAndDeletedAtIsNull(owner, pageable)
                : sessions.findByOwnerIdAndDeletedAtIsNullAndSnapshotScopeTypeAndSnapshotTopicIdAndSnapshotTopicItemId(
                        owner, scope.type(), scope.topicId(), scope.topicItemId(), pageable);
        return new Page<>(rows.getContent().stream().map(this::view).toList(), page, size, rows.hasNext());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Page<Turn> turns(String id, int page, int size) {
        page(page, size);
        SpeakingSession session = owned(id, users.requireCurrentUserId());
        Map<Integer, SpeakingTurnVo> visible = new TreeMap<>();
        for (SpeakingTurn row : turns.findBySessionIdOrderByValueSequenceAsc(id)) {
            if (row.getValue().getSequence() <= session.getLastTurnSequence()) visible.put(row.getValue().getSequence(), row.getValue());
        }
        var active = session.getActiveTurn();
        if (active != null) visible.put(active.getSequence(), active);
        List<Turn> fetched = visible.values().stream().skip((long) page * size).limit(size + 1L).map(this::viewTurn).toList();
        return new Page<>(List.copyOf(fetched.subList(0, Math.min(size, fetched.size()))), page, size, fetched.size() > size);
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public SpeakingAssessmentResponse assessment(String id) {
        SpeakingSession session = owned(id, users.requireCurrentUserId());
        return SpeakingAssessmentResponse.pending(id, session.getAssessmentStatus());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public void delete(String id) {
        deleteOwned(id, users.requireCurrentUserId());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public void deleteAll() {
        String owner = users.requireCurrentUserId();
        var page = PageRequest.of(0, 100, HISTORY_ORDER);
        List<SpeakingSession> batch;
        do {
            batch = sessions.findByOwnerIdAndDeletedAtIsNull(owner, page).getContent();
            for (SpeakingSession session : batch) {
                try {
                    deleteOwned(session.getId(), owner);
                } catch (SpeakingException error) {
                    if (!"SESSION_NOT_FOUND".equals(error.code())) throw error;
                }
            }
        } while (!batch.isEmpty());
    }

    private void deleteOwned(String id, String owner) {
        SpeakingSession deleted = change(id, current -> {
            requireOwner(current, owner);
            Instant instant = now();
            current.setDeletedAt(instant);
            current.setEndedAt(current.getEndedAt() == null ? instant : current.getEndedAt());
            current.setEndReason(current.getEndReason() == null ? SpeakingEndReason.DELETED : current.getEndReason());
            current.setStatus(SpeakingSessionStatus.ENDED);
            current.setUnfinishedOwner(null);
            current.setReplacement(null);
            current.setActiveTurn(null);
            clearConnection(current);
            return true;
        });
        finishSlot(deleted);
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Audio turnAudio(String id, String turnId, String kind) {
        SpeakingSession session = owned(id, users.requireCurrentUserId());
        SpeakingAudioKind audioKind;
        try {
            audioKind = SpeakingAudioKind.valueOf(kind);
        } catch (IllegalArgumentException error) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Unknown audio kind", false);
        }
        var turn = turn(session, turnId);
        return audio(audioKind == SpeakingAudioKind.LEARNER_RECORDING ? turn.getLearnerAudio() : turn.getAssistantAudio());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public Audio openingAudio(String id) {
        var opening = owned(id, users.requireCurrentUserId()).getOpening();
        return audio(opening == null ? null : opening.getAudio());
    }

    private Audio audio(SpeakingMediaVo ref) {
        if (!SpeakingSessionMapper.audioAvailable(ref, now())) throw noAudio();
        try {
            var delivery = media.resolve(ref.getMediaId());
            if (delivery.expiresAt() == null || delivery.url() == null || !delivery.expiresAt().isAfter(now())) throw noAudio();
            long seconds = Duration.between(now(), delivery.expiresAt()).toSeconds();
            return new Audio(delivery.url(), Math.min(60, Math.max(1, seconds)));
        } catch (MediaNotFoundException error) {
            throw noAudio();
        } catch (MediaStorageUnavailableException error) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_UNAVAILABLE", "Audio delivery is temporarily unavailable", true);
        }
    }

    @Override
    public Lease consumeTicket(String sessionId, String ticket) {
        if (ticket == null || ticket.length() != 72) throw invalidTicket();
        SpeakingSession session;
        try {
            session = change(sessionId, current -> {
                if (current.getStatus() == SpeakingSessionStatus.ENDED || current.getTicketHash() == null
                        || !current.getTicketExpiresAt().isAfter(now()) || !secureEquals(current.getTicketHash(), hash(ticket))
                        || current.getLeaseId() != null) throw invalidTicket();
                current.setTicketHash(null);
                current.setTicketExpiresAt(null);
                current.setLeaseId(id());
                current.setLeaseInstance(instance);
                return true;
            });
        } catch (SpeakingException error) {
            if ("SESSION_NOT_FOUND".equals(error.code())) throw invalidTicket();
            throw error;
        }
        return new Lease(sessionId, session.getOwnerId(), session.getControllerEpoch(), session.getLeaseId());
    }

    @Override
    public Session state(Lease lease) {
        SpeakingSession session = runtimeSession(lease.sessionId());
        requireLease(session, lease);
        return view(session);
    }

    @Override
    public void releaseLease(Lease lease) {
        change(lease.sessionId(), current -> {
            if (!leaseMatches(current, lease)) return false;
            discardReceiving(current, "TURN_CANCELLED");
            clearConnection(current);
            return true;
        }, true);
    }

    @Override
    public Turn admit(Lease lease, UUID clientTurnId) {
        String clientId = clientTurnId.toString();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            SpeakingSession session = runtimeSession(lease.sessionId());
            requireLease(session, lease);
            var current = session.getActiveTurn();
            if (current != null && clientId.equals(current.getClientTurnId())) return viewTurn(current);
            var previous = turns.findBySessionIdAndValueClientTurnId(session.getId(), clientId);
            if (previous.isPresent()) return viewTurn(previous.get().getValue());
            requireActive(session);
            if (current != null) {
                if (!current.getStatus().terminal()) {
                    throw failure(HttpStatus.CONFLICT, "TURN_IN_PROGRESS", "A turn is already in progress", true);
                }
                flushTerminal(session);
                continue;
            }
            SpeakingEndReason limit = admissionLimit(session);
            if (limit != null) {
                beginClosing(session, limit);
                if (trySave(session) == null) continue;
                SpeakingSession latest = advance(session.getId());
                if (latest.getStatus() == SpeakingSessionStatus.ENDED) finishSlot(latest);
                if (limit == SpeakingEndReason.DAILY_QUOTA_REACHED) throw quotaReached();
                throw ended();
            }
            int sequence = session.getLastTurnSequence() + 1;
            SpeakingTurnVo admitted = SpeakingTurnVo.builder().turnId(id()).clientTurnId(clientId).sequence(sequence)
                    .status(SpeakingTurnStatus.RECEIVING).build();
            session.setLastTurnSequence(sequence);
            session.setActiveTurn(admitted);
            if (trySave(session) != null) return viewTurn(admitted);
        }
        throw busy();
    }

    @Override
    public void recordingStarted(Lease lease, String turnId) {
        change(lease.sessionId(), current -> {
            requireLease(current, lease);
            var turn = activeTurn(current, turnId);
            if (turn.getStatus() != SpeakingTurnStatus.RECEIVING) throw staleTurn();
            if (turn.getRecordingStartedAt() != null) return false;
            turn.setRecordingStartedAt(now());
            return true;
        });
    }

    @Override
    public Turn cancel(Lease lease, String turnId) {
        SpeakingSession saved = change(lease.sessionId(), current -> {
            requireLease(current, lease);
            var turn = activeTurn(current, turnId);
            if (turn.getStatus() != SpeakingTurnStatus.RECEIVING) throw staleTurn();
            return discardReceiving(current, "TURN_CANCELLED");
        });
        Turn response = viewTurn(saved.getActiveTurn());
        flushTerminal(saved);
        return response;
    }

    @Override
    public TurnWork seal(Lease lease, String turnId, SpeakingMediaVo audio, long durationMs) {
        String token = id();
        change(lease.sessionId(), current -> {
            requireLease(current, lease);
            requireActive(current);
            var turn = activeTurn(current, turnId);
            if (turn.getStatus() != SpeakingTurnStatus.RECEIVING) throw staleTurn();
            if (durationMs < 0 || durationMs > current.getLimits().getMaxTurnAudioMs()) {
                throw failure(HttpStatus.BAD_REQUEST, "AUDIO_LIMIT", "The turn audio exceeds its limit", false);
            }
            turn.setSealedAt(now());
            turn.setLearnerAudio(audio);
            turn.setSpeechDurationMs(durationMs);
            turn.setWorkToken(token);
            turn.setStatus(SpeakingTurnStatus.TRANSCRIBING);
            return true;
        });
        return new TurnWork(lease.sessionId(), turnId, token);
    }

    @Override
    public TurnWork retry(Lease lease, String turnId) {
        String token = id();
        change(lease.sessionId(), current -> {
            requireLease(current, lease);
            requireActive(current);
            var previous = turn(current, turnId);
            if (previous.getStatus() != SpeakingTurnStatus.FAILED || previous.getAttempt() >= 1
                    || previous.getTranscript() == null || previous.getTranscript().isBlank()
                    || !"CONVERSATION_UNAVAILABLE".equals(previous.getReason())
                    || previous.getSequence() != current.getLastTurnSequence()) throw staleTurn();
            previous.setRevision(previous.getRevision() + 1);
            previous.setAttempt(previous.getAttempt() + 1);
            previous.setStatus(SpeakingTurnStatus.GENERATING);
            previous.setReason(null);
            previous.setWorkToken(token);
            current.setActiveTurn(previous);
            return true;
        });
        return new TurnWork(lease.sessionId(), turnId, token);
    }

    @Override
    public void transcript(TurnWork work, String original) {
        if (original == null || original.isBlank()) throw failure(HttpStatus.BAD_REQUEST, "EMPTY_AUDIO", "No speech was detected", false);
        change(work.sessionId(), session -> {
            SpeakingTurnVo turn = workTurn(session, work);
            if (turn.getStatus() != SpeakingTurnStatus.TRANSCRIBING) throw staleTurn();
            turn.setTranscript(original);
            turn.setStatus(SpeakingTurnStatus.GENERATING);
            return true;
        });
    }

    @Override
    public Turn settle(TurnWork work, SpeakingTurnStatus result, String reason, String reply, SpeakingHintVo hint,
                       SpeakingCorrectionVo correction) {
        if (!result.terminal()) throw new IllegalArgumentException("A settled turn must be terminal");
        SpeakingSession saved = change(work.sessionId(), session -> {
            SpeakingTurnVo turn = workTurn(session, work);
            if (result == SpeakingTurnStatus.COMMITTED) {
                if (turn.getStatus() != SpeakingTurnStatus.GENERATING || reply == null || reply.isBlank()) throw staleTurn();
                turn.setReply(reply);
                turn.setHint(hint);
                turn.setCorrection(correction);
                turn.setAudioStatus(SpeakingAudioStatus.TEXT_READY);
                session.setCommittedTurnCount(session.getCommittedTurnCount() + 1);
            }
            finalizeTurn(session, turn, result, reason);
            return true;
        });
        Turn response = viewTurn(saved.getActiveTurn());
        SpeakingSession latest = advance(saved.getId());
        if (latest.getStatus() == SpeakingSessionStatus.ENDED) finishSlot(latest);
        return response;
    }

    @Override
    public SpeakingSession runtimeSession(String sessionId) {
        return sessions.findById(sessionId).filter(session -> session.getDeletedAt() == null)
                .orElseThrow(SpeakingSessionServiceImpl::missing);
    }

    @Override
    public boolean completeOpening(String sessionId, SpeakingOpeningVo opening) {
        if (opening == null || opening.getReply() == null || opening.getReply().isBlank()) {
            throw new IllegalArgumentException("A completed opening requires a reply");
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            SpeakingSession session = runtimeSession(sessionId);
            if (session.getStatus() != SpeakingSessionStatus.INITIALIZING || timeLimitReached(session)) return false;
            session.setOpening(opening);
            session.setStartedAt(now());
            session.setStatus(SpeakingSessionStatus.ACTIVE);
            if (trySave(session) != null) return true;
        }
        throw busy();
    }

    @Override
    public void recover() {
        for (SpeakingSession session : sessions.findByStatusInAndDeletedAtIsNull(EnumSet.of(
                SpeakingSessionStatus.INITIALIZING, SpeakingSessionStatus.ACTIVE, SpeakingSessionStatus.CLOSING))) {
            if (session.getActiveTurn() != null && session.getActiveTurn().getStatus() == SpeakingTurnStatus.RECEIVING
                    && !instance.equals(session.getLeaseInstance())) {
                change(session.getId(), current -> !instance.equals(current.getLeaseInstance())
                        && discardReceiving(current, "SYSTEM_INTERRUPTED"), true);
            }
            SpeakingSession latest = advance(session.getId());
            if (latest.getStatus() == SpeakingSessionStatus.ENDED) finishSlot(latest);
        }
        for (SpeakingOwnerSlot waiting : owners.findByStartIsNotNull()) reconcile(waiting);
    }

    private SpeakingSession advance(String id) {
        SpeakingSession advanced = change(id, current -> {
            if ((current.getStatus() == SpeakingSessionStatus.INITIALIZING || current.getStatus() == SpeakingSessionStatus.ACTIVE)
                    && timeLimitReached(current)) {
                beginClosing(current, SpeakingEndReason.TIME_LIMIT_REACHED);
                return true;
            }
            if (current.getStatus() != SpeakingSessionStatus.CLOSING) return false;
            var turn = current.getActiveTurn();
            if (turn != null && !turn.getStatus().terminal() && !current.getClosingCutoff().isAfter(now())) {
                finalizeTurn(current, turn, SpeakingTurnStatus.FAILED, "SYSTEM_INTERRUPTED");
                return true;
            }
            if (turn != null) return false;
            current.setStatus(SpeakingSessionStatus.ENDED);
            current.setEndedAt(now());
            current.setUnfinishedOwner(null);
            clearConnection(current);
            return true;
        }, true);
        if (advanced.getDeletedAt() != null) return advanced;
        if (advanced.getActiveTurn() != null && advanced.getActiveTurn().getStatus().terminal()) {
            advanced = flushTerminal(advanced);
        }
        if (advanced.getStatus() == SpeakingSessionStatus.CLOSING && advanced.getActiveTurn() == null) return advance(id);
        return advanced;
    }

    private SpeakingSession flushTerminal(SpeakingSession session) {
        var marker = session.getActiveTurn();
        if (marker == null || !marker.getStatus().terminal() || session.getDeletedAt() != null) return session;
        archive.record(session.getId(), marker, now());
        return change(session.getId(), current -> {
            var latest = current.getActiveTurn();
            if (latest == null || !latest.getTurnId().equals(marker.getTurnId()) || latest.getRevision() != marker.getRevision()
                    || !latest.getStatus().terminal()) return false;
            current.setActiveTurn(null);
            return true;
        }, true);
    }

    private void beginClosing(SpeakingSession session, SpeakingEndReason reason) {
        if (session.getStatus() == SpeakingSessionStatus.CLOSING || session.getStatus() == SpeakingSessionStatus.ENDED) return;
        session.setStatus(SpeakingSessionStatus.CLOSING);
        session.setEndReason(reason);
        session.setClosingCutoff(now().plusMillis(session.getLimits().getClosingGraceMs()));
        session.setAssessmentStatus(SpeakingAssessmentStatus.FINALIZING);
        discardReceiving(session, "TURN_CANCELLED");
    }

    private boolean discardReceiving(SpeakingSession session, String reason) {
        var turn = session.getActiveTurn();
        if (turn == null || turn.getStatus() != SpeakingTurnStatus.RECEIVING) return false;
        finalizeTurn(session, turn, SpeakingTurnStatus.SKIPPED, reason);
        return true;
    }

    private void finalizeTurn(SpeakingSession session, SpeakingTurnVo turn, SpeakingTurnStatus status, String reason) {
        turn.setRevision(turn.getRevision() + 1);
        turn.setStatus(status);
        turn.setReason(reason);
        turn.setWorkToken(null);
        if (turn.getSealedAt() != null && turn.getAttempt() == 0) {
            session.setFinalizedTurnCount(session.getFinalizedTurnCount() + 1);
            session.setSpeechDurationMs(session.getSpeechDurationMs() + turn.getSpeechDurationMs());
            if (session.getFinalizedTurnCount() >= session.getLimits().getMaxTurns()) {
                beginClosing(session, SpeakingEndReason.TURN_LIMIT_REACHED);
            }
        }
    }

    private SpeakingEndReason admissionLimit(SpeakingSession session) {
        if (timeLimitReached(session)) return SpeakingEndReason.TIME_LIMIT_REACHED;
        if (session.getFinalizedTurnCount() >= session.getLimits().getMaxTurns()) return SpeakingEndReason.TURN_LIMIT_REACHED;
        return hasQuota(session.getOwnerId()) ? null : SpeakingEndReason.DAILY_QUOTA_REACHED;
    }

    private boolean timeLimitReached(SpeakingSession session) {
        return !session.getCreatedAt().plusMillis(session.getLimits().getMaxSessionMs()).isAfter(now());
    }

    private boolean hasQuota(String owner) {
        try {
            return quota.checkAvailability(owner, QUOTA_TYPE).remaining() > 0;
        } catch (QuotaUnavailableException unavailable) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "QUOTA_UNAVAILABLE", "Speaking quota is temporarily unavailable", true);
        }
    }

    private SpeakingStartVo newStart(String owner, SpeakingScopeDto scope, String requestId, String source, Long epoch) {
        var access = topicAccess.resolve(owner, scope);
        if (access.reason() != null) {
            throw failure(HttpStatus.FORBIDDEN, access.reason().name(), "This Speaking scope is unavailable", false);
        }
        if (!hasQuota(owner)) throw quotaReached();
        return new SpeakingStartVo(id(), requestId, source, epoch, SpeakingSessionMapper.snapshot(access.snapshot()), settings.limits());
    }

    private StartResult replay(String owner, String request, SpeakingScopeDto scope, String source, Long epoch) {
        var prior = sessions.findByOwnerIdAndClientRequestId(owner, request).orElse(null);
        if (prior == null) return null;
        if (!scope.equals(SpeakingSessionMapper.scope(prior.getSnapshot()))
                || !Objects.equals(source, prior.getSourceSessionId()) || !Objects.equals(epoch, prior.getSourceEpoch())) {
            throw requestConflict();
        }
        if (prior.getDeletedAt() != null) throw missing();
        return new StartResult(view(prior), false, null);
    }

    private SpeakingOwnerSlot slot(String owner) {
        return owners.findById(owner).orElseGet(() -> SpeakingOwnerSlot.builder().id(owner).build());
    }

    private SpeakingSession reconcile(SpeakingOwnerSlot slot) {
        var intent = slot.getStart();
        if (intent == null) return null;
        SpeakingSession reserved = materialize(slot.getId(), intent);
        if (reserved.getStatus() != SpeakingSessionStatus.ENDED) return reserved;
        finishSlot(reserved);
        SpeakingOwnerSlot latest = slot(slot.getId());
        if (latest.getStart() != null && !latest.getStart().getSessionId().equals(reserved.getId())) {
            return materialize(latest.getId(), latest.getStart());
        }
        return null;
    }

    private SpeakingSession materialize(String owner, SpeakingStartVo intent) {
        return sessions.findById(intent.getSessionId()).orElseGet(() -> {
            try {
                return sessions.insert(SpeakingSession.builder().id(intent.getSessionId()).ownerId(owner).unfinishedOwner(owner)
                        .clientRequestId(intent.getClientRequestId()).sourceSessionId(intent.getSourceSessionId())
                        .sourceEpoch(intent.getSourceEpoch()).snapshot(intent.getSnapshot()).limits(intent.getLimits())
                        .controllerEpoch(1).createdAt(now()).status(SpeakingSessionStatus.INITIALIZING)
                        .assessmentStatus(SpeakingAssessmentStatus.PENDING).build());
            } catch (DuplicateKeyException duplicate) {
                return sessions.findById(intent.getSessionId()).orElseThrow(SpeakingSessionServiceImpl::busy);
            }
        });
    }

    private void finishSlot(SpeakingSession observed) {
        String owner = observed.getOwnerId();
        SpeakingOwnerSlot slot = slot(owner);
        if (slot.getStart() == null || !observed.getId().equals(slot.getStart().getSessionId())) return;
        SpeakingSession latest = sessions.findById(observed.getId()).orElse(observed);
        SpeakingStartVo replacement = latest.getDeletedAt() == null ? latest.getReplacement() : null;
        slot.setStart(replacement);
        if (saveSlot(slot) && replacement != null) materialize(owner, replacement);
    }

    private boolean saveSlot(SpeakingOwnerSlot slot) {
        try {
            owners.save(slot);
            return true;
        } catch (OptimisticLockingFailureException | DuplicateKeyException contention) {
            return false;
        }
    }

    private SpeakingSession owned(String id, String owner) {
        var session = runtimeSession(id);
        requireOwner(session, owner);
        return session;
    }

    private void requireOwner(SpeakingSession session, String owner) {
        if (!Objects.equals(owner, session.getOwnerId())) {
            throw failure(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied", false);
        }
    }

    private interface Mutation { boolean apply(SpeakingSession session); }

    private SpeakingSession change(String id, Mutation mutation) {
        return change(id, mutation, false);
    }

    private SpeakingSession change(String id, Mutation mutation, boolean includeDeleted) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            SpeakingSession session = sessions.findById(id).orElseThrow(SpeakingSessionServiceImpl::missing);
            if (session.getDeletedAt() != null) {
                if (includeDeleted) return session;
                throw missing();
            }
            if (!mutation.apply(session)) return session;
            SpeakingSession saved = trySave(session);
            if (saved != null) return saved;
        }
        throw busy();
    }

    private SpeakingSession trySave(SpeakingSession session) {
        try {
            return sessions.save(session);
        } catch (OptimisticLockingFailureException contention) {
            return null;
        }
    }

    private SpeakingTurnVo turn(SpeakingSession session, String turnId) {
        var active = session.getActiveTurn();
        if (active != null && turnId.equals(active.getTurnId())) return active;
        return turns.findByIdAndSessionId(turnId, session.getId()).map(SpeakingTurn::getValue)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "STALE_TURN", "The Speaking turn was not found", false));
    }

    private SpeakingTurnVo activeTurn(SpeakingSession session, String turnId) {
        var turn = session.getActiveTurn();
        if (turn == null || !Objects.equals(turn.getTurnId(), turnId)) throw staleTurn();
        return turn;
    }

    private SpeakingTurnVo workTurn(SpeakingSession session, TurnWork work) {
        if (session.getStatus() == SpeakingSessionStatus.ENDED || session.getStatus() == SpeakingSessionStatus.CLOSING
                && !session.getClosingCutoff().isAfter(now())) throw staleTurn();
        var turn = activeTurn(session, work.turnId());
        if (!Objects.equals(turn.getWorkToken(), work.token()) || turn.getStatus().terminal()
                || turn.getStatus() == SpeakingTurnStatus.RECEIVING) throw staleTurn();
        return turn;
    }

    private void requireLease(SpeakingSession session, Lease lease) {
        if (!leaseMatches(session, lease)) throw controllerChanged();
    }

    private boolean leaseMatches(SpeakingSession session, Lease lease) {
        return session.getControllerEpoch() == lease.epoch() && instance.equals(session.getLeaseInstance())
                && Objects.equals(session.getId(), lease.sessionId()) && Objects.equals(session.getOwnerId(), lease.ownerId())
                && session.getLeaseId() != null && Objects.equals(session.getLeaseId(), lease.leaseId());
    }

    private static void clearConnection(SpeakingSession session) {
        session.setLeaseId(null);
        session.setLeaseInstance(null);
        session.setTicketHash(null);
        session.setTicketExpiresAt(null);
    }

    private static void requireControlled(SpeakingSession session) {
        if (session.getStatus() == SpeakingSessionStatus.CLOSING) throw closing();
        if (session.getStatus() == SpeakingSessionStatus.ENDED) throw ended();
    }

    private static void requireActive(SpeakingSession session) {
        requireControlled(session);
        if (session.getStatus() == SpeakingSessionStatus.INITIALIZING) {
            throw failure(HttpStatus.CONFLICT, "OPENING_PENDING", "The opening is still processing", true);
        }
    }

    private static void requireEpoch(SpeakingSession session, long expected) {
        if (session.getControllerEpoch() != expected) throw controllerChanged();
    }

    private static void validateScope(SpeakingScopeDto scope) {
        if (scope == null || !scope.valid()) throw failure(HttpStatus.BAD_REQUEST, "INVALID_SCOPE", "Invalid Speaking scope", false);
    }

    private static PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Page must be non-negative and size between 1 and 100", false);
        }
        return PageRequest.of(page, size, HISTORY_ORDER);
    }

    private Session view(SpeakingSession session) { return SpeakingSessionMapper.session(session, now()); }

    private Turn viewTurn(SpeakingTurnVo turn) { return SpeakingSessionMapper.turn(turn, now()); }

    private Instant now() { return clock.instant(); }

    private static String id() { return UUID.randomUUID().toString(); }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static SpeakingException failure(HttpStatus status, String code, String detail, boolean retryable) {
        return new SpeakingException(status, code, detail, retryable);
    }

    private static SpeakingException missing() { return failure(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Speaking session was not found", false); }
    private static SpeakingException closing() { return failure(HttpStatus.CONFLICT, "SESSION_CLOSING", "The Speaking session is closing", true); }
    private static SpeakingException ended() { return failure(HttpStatus.CONFLICT, "SESSION_ENDED", "The Speaking session has ended", false); }
    private static SpeakingException busy() { return failure(HttpStatus.CONFLICT, "REQUEST_CONFLICT", "Speaking state changed. Retry the request.", true); }
    private static SpeakingException requestConflict() { return failure(HttpStatus.CONFLICT, "REQUEST_CONFLICT", "The request ID was used for a different operation", false); }
    private static SpeakingException controllerChanged() { return failure(HttpStatus.CONFLICT, "CONTROLLER_CHANGED", "The Speaking controller has changed", false); }
    private static SpeakingException invalidTicket() { return failure(HttpStatus.UNAUTHORIZED, "INVALID_TICKET", "The Speaking socket ticket is invalid", true); }
    private static SpeakingException staleTurn() { return failure(HttpStatus.CONFLICT, "STALE_TURN", "The Speaking turn is no longer available for this command", false); }
    private static SpeakingException quotaReached() { return failure(HttpStatus.TOO_MANY_REQUESTS, "DAILY_QUOTA_REACHED", "The daily Speaking quota is exhausted", true); }
    private static SpeakingException noAudio() { return failure(HttpStatus.NOT_FOUND, "MEDIA_UNAVAILABLE", "The Speaking audio is unavailable", false); }
}
