package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.speaking.dto.response.SpeakingSessionResponse;
import fit.iuh.engolearn.speaking.model.DialogueDecision;
import fit.iuh.engolearn.speaking.model.SpeakingAudioAsset;
import fit.iuh.engolearn.speaking.model.SpeakingDemoAssessment;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.SpeakingUsageSegment;
import fit.iuh.engolearn.speaking.model.ValidatedTurnProposal;
import fit.iuh.engolearn.speaking.utils.port.ConversationEvaluator;
import fit.iuh.engolearn.speaking.utils.port.SpeechStreamPort;
import fit.iuh.engolearn.speaking.utils.port.SpeechSynthesisPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class SpeakingRealtimeService {
    private static final Logger log = LoggerFactory.getLogger(SpeakingRealtimeService.class);
    private static final int MAX_PCM_BYTES = 16000 * 2 * 120;
    private final SpeakingSessionService sessions;
    private final SpeechStreamPort stt;
    private final ConversationEvaluator evaluator;
    private final SpeechSynthesisPort tts;
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, Connection> currentControllers = new ConcurrentHashMap<>();
    private final Map<String, Object> controllerLocks = new ConcurrentHashMap<>();
    private volatile SpeakingDemoComposer demoComposer;
    private volatile boolean demoManagerEnabled;

    private static final class Connection {
        private final String connectionId;
        private final String sessionId;
        private final String learnerId;
        private final long epoch;
        private final Consumer<Map<String, Object>> emit;
        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        private volatile String turnId;
        private volatile Instant turnStarted;
        private volatile boolean processing;
        private volatile boolean active = true;

        private Connection(String connectionId, String sessionId, String learnerId, long epoch,
                           Consumer<Map<String, Object>> emit) {
            this.connectionId = connectionId;
            this.sessionId = sessionId;
            this.learnerId = learnerId;
            this.epoch = epoch;
            this.emit = emit;
        }
    }

    @Autowired
    public SpeakingRealtimeService(SpeakingSessionService sessions, SpeechStreamPort stt,
                                   ConversationEvaluator evaluator, SpeechSynthesisPort tts) {
        this(sessions, stt, evaluator, tts, null, false);
    }

    SpeakingRealtimeService(SpeakingSessionService sessions, SpeechStreamPort stt,
                            ConversationEvaluator evaluator, SpeechSynthesisPort tts,
                            SpeakingDemoComposer demoComposer, boolean demoManagerEnabled) {
        this.sessions = sessions;
        this.stt = stt;
        this.evaluator = evaluator;
        this.tts = tts;
        this.demoComposer = demoComposer;
        this.demoManagerEnabled = demoManagerEnabled;
    }

    @Autowired(required = false)
    void configureDemo(Environment environment,
                       @Value("${engolearn.speaking.demo.enabled:false}") boolean demoEnabled,
                       @Value("${engolearn.speaking.demo.manager.enabled:false}") boolean managerEnabled,
                       SpeakingDemoComposer composer) {
        this.demoManagerEnabled = demoEnabled && managerEnabled
                && java.util.Arrays.stream(environment.getActiveProfiles()).noneMatch("prod"::equals);
        this.demoComposer = composer;
    }

    public CompletableFuture<Void> open(String connectionId, String sessionId, String ticket,
                                        Consumer<Map<String, Object>> emit) {
        SpeakingSession session = sessions.consumeTicket(ticket, sessionId);
        Connection connection = new Connection(connectionId, sessionId, session.getLearnerId(),
                session.getControllerEpoch(), emit);
        Connection previous;
        synchronized (controllerLock(sessionId)) {
            previous = currentControllers.get(sessionId);
            if (previous != null) {
                previous.active = false;
                connections.remove(previous.connectionId, previous);
            }
            connections.put(connectionId, connection);
            currentControllers.put(sessionId, connection);
        }
        recoverReplacedTurn(previous);
        return stt.open(sessionId, new SpeechStreamPort.Listener() {
            @Override public void onInterim(String text) {
                if (connection.turnId != null) send(connection, "INTERIM_TRANSCRIPT",
                        connection.turnId, Map.of("text", text));
            }
            @Override public void onFailure(String message) {
                send(connection, "STT_ERROR", connection.turnId, Map.of("message", message));
            }
        }).thenRun(() -> send(connection, "CONNECTED", null, Map.of(
                "sessionId", sessionId, "snapshot", sessions.socketSnapshot(sessionId, connection.learnerId))));
    }

    public void start(String connectionId, String turnId, long epoch) {
        Connection c = require(connectionId);
        sessions.requireSocketController(c.sessionId, c.learnerId, epoch);
        if (c.epoch != epoch || c.turnId != null || c.processing) throw new IllegalStateException("Turn is busy");
        if (!turnId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid turn ID");
        stt.beginTurn(c.sessionId);
        c.turnId = turnId;
        c.turnStarted = Instant.now();
        c.pcm.reset();
        send(c, "STT_READY", turnId, Map.of());
    }

    public void pcm(String connectionId, byte[] frame) {
        Connection c = require(connectionId);
        if (c.turnId == null || c.processing || c.turnStarted == null) return;
        if (Duration.between(c.turnStarted, Instant.now()).toSeconds() > 120
                || c.pcm.size() + frame.length > MAX_PCM_BYTES) {
            send(c, "TURN_FAILED", c.turnId, Map.of("message", "Turn exceeds 120 seconds"));
            c.turnId = null;
            return;
        }
        sessions.requireSocketController(c.sessionId, c.learnerId, c.epoch);
        stt.send(c.sessionId, frame);
        c.pcm.writeBytes(frame);
    }

    public void end(String connectionId, String turnId, long epoch) {
        Connection c = require(connectionId);
        sessions.requireSocketController(c.sessionId, c.learnerId, epoch);
        if (c.epoch != epoch || !turnId.equals(c.turnId) || c.processing) {
            throw new IllegalStateException("Turn does not match active recording");
        }
        c.processing = true;
        Instant ended = Instant.now();
        byte[] recording = c.pcm.toByteArray();
        stt.endTurn(c.sessionId).whenCompleteAsync((transcript, error) -> {
            try {
                if (error != null) {
                    send(c, "TURN_FAILED", turnId, Map.of("message", "Speech recognition failed"));
                    return;
                }
                if (transcript == null || transcript.isBlank()) {
                    send(c, "TURN_EMPTY", turnId, Map.of());
                    return;
                }
                SpeakingTurn turn = sessions.commit(c.sessionId, c.learnerId, epoch, turnId, transcript);
                sessions.recordUsage(c.sessionId, turnId, SpeakingUsageSegment.Kind.LEARNER_SPEECH,
                        c.turnStarted, ended);
                send(c, "TURN_ACCEPTED", turnId, Map.of("text", transcript));
                evaluate(c, turn, recording);
            } catch (Exception ex) {
                send(c, "TURN_FAILED", turnId, Map.of("message", ex.getMessage()));
            } finally {
                c.turnId = null;
                c.processing = false;
                c.pcm.reset();
            }
        }, workers);
    }

    public void cancel(String connectionId, String turnId, long epoch) {
        Connection c = require(connectionId);
        sessions.requireSocketController(c.sessionId, c.learnerId, epoch);
        if (c.epoch != epoch || !turnId.equals(c.turnId) || c.processing) return;
        stt.cancelTurn(c.sessionId);
        c.turnId = null;
        c.turnStarted = null;
        c.pcm.reset();
        send(c, "TURN_CANCELED", turnId, Map.of());
    }

    private void evaluate(Connection c, SpeakingTurn turn, byte[] recording) {
        String turnId = turn.getId();
        if (demoManagerEnabled && demoComposer != null) {
            evaluateDemo(c, turn, recording);
            return;
        }
        SpeakingSessionResponse snapshot = sessions.socketSnapshot(c.sessionId, c.learnerId);
        List<ConversationEvaluator.Exchange> history = new ArrayList<>();
        for (SpeakingSessionResponse.Turn previous : snapshot.turns()) {
            if (!previous.id().equals(turnId) && previous.evaluationState() == SpeakingTurn.EvaluationState.SUCCEEDED) {
                history.add(new ConversationEvaluator.Exchange(previous.transcript(), previous.aiReply()));
            }
        }
        Instant waitStarted = Instant.now();
        try {
            ConversationEvaluator.Result result = evaluator.evaluate(snapshot.content(), turn.getTranscript(),
                    history.stream().skip(Math.max(0, history.size() - 5)).toList(), snapshot.achievedGoalIds());
            sessions.assessed(c.sessionId, turnId, result);
            sessions.recordUsage(c.sessionId, turnId, SpeakingUsageSegment.Kind.AI_WAIT,
                    waitStarted, min(Instant.now(), waitStarted.plusSeconds(10)));
            send(c, "TURN_RESULT", turnId, Map.of("snapshot", sessions.socketSnapshot(c.sessionId, c.learnerId)));
            try {
                sessions.saveAudio(c.sessionId, turnId, SpeakingAudioAsset.Kind.LEARNER_RECORDING,
                        "wav", "audio/wav", wav(recording));
            } catch (Exception ex) {
                send(c, "AUDIO_ERROR", turnId, Map.of("kind", "LEARNER_RECORDING"));
            }
            if (result.reply() != null && !result.reply().isBlank()) {
                Instant replyStarted = Instant.now();
                try {
                    byte[] spoken = tts.synthesize(result.reply());
                    if (spoken.length == 0) throw new IllegalStateException("Cartesia returned empty audio");
                    sessions.saveAudio(c.sessionId, turnId, SpeakingAudioAsset.Kind.AI_REPLY,
                            "mp3", "audio/mpeg", spoken);
                    sessions.recordUsage(c.sessionId, turnId, SpeakingUsageSegment.Kind.AI_REPLY,
                            replyStarted, Instant.now());
                    send(c, "AI_AUDIO_READY", turnId, Map.of());
                } catch (Exception ex) {
                    log.warn("AI audio failed for session {} turn {}", c.sessionId, turnId, ex);
                    send(c, "TTS_ERROR", turnId, Map.of("message", "AI audio is unavailable"));
                }
            }
        } catch (Exception ex) {
            log.warn("Speaking assessment failed for session {} turn {}", c.sessionId, turnId, ex);
            sessions.assessmentFailed(c.sessionId, turnId, c.epoch);
            send(c, "PROCESSING_ERROR", turnId, Map.of("message", "Assessment is unavailable",
                    "snapshot", sessions.socketSnapshot(c.sessionId, c.learnerId)));
        }
    }

    private void evaluateDemo(Connection c, SpeakingTurn turn, byte[] recording) {
        String turnId = turn.getId();
        Instant waitStarted = Instant.now();
        try {
            SpeakingSession authoritative = sessions.demoState(c.sessionId, c.learnerId);
            if (!eligibleController(authoritative, c)) return;
            SpeakingSessionResponse snapshot = sessions.socketSnapshot(c.sessionId, c.learnerId);
            SpeakingDemoAssessment assessment = demoComposer.assess(authoritative, snapshot, turn.getTranscript());
            if (!eligibleController(sessions.demoState(c.sessionId, c.learnerId), c)
                    || !sessions.settleDemo(c.sessionId, turnId, c.epoch, assessment)) return;
            sessions.recordUsage(c.sessionId, turnId, SpeakingUsageSegment.Kind.AI_WAIT,
                    waitStarted, min(Instant.now(), waitStarted.plusSeconds(10)));

            if (!eligibleController(sessions.demoState(c.sessionId, c.learnerId), c)) return;

            DialogueDecision decision = assessment.decision();
            Map<String, Object> diagnostic = new java.util.LinkedHashMap<>();
            diagnostic.put("demoDecision", SpeakingDemoDecisionEvent.from(decision));
            if (assessment.proposal().validityOutcome() == ValidatedTurnProposal.ValidityOutcome.EVALUATION_FAILED) {
                diagnostic.put("message", "Assessment is unavailable");
                diagnostic.put("snapshot", sessions.socketSnapshot(c.sessionId, c.learnerId));
                send(c, "PROCESSING_ERROR", turnId, diagnostic);
            } else {
                diagnostic.put("snapshot", sessions.socketSnapshot(c.sessionId, c.learnerId));
                send(c, "TURN_RESULT", turnId, diagnostic);
            }
            try {
                sessions.saveAudio(c.sessionId, turnId, SpeakingAudioAsset.Kind.LEARNER_RECORDING,
                        "wav", "audio/wav", wav(recording));
            } catch (Exception ex) {
                send(c, "AUDIO_ERROR", turnId, Map.of("kind", "LEARNER_RECORDING"));
            }
            if (decision.outcome() == DialogueDecision.Outcome.NORMAL
                    && decision.spokenReplyToDeliver() != null
                    && !decision.spokenReplyToDeliver().isBlank()) {
                deliverDemoReply(c, turnId, decision.spokenReplyToDeliver());
            }
        } catch (Exception ex) {
            log.warn("Speaking demo assessment failed for session {} turn {}", c.sessionId, turnId, ex);
            if (!eligibleController(sessions.demoState(c.sessionId, c.learnerId), c)) return;
            sessions.assessmentFailed(c.sessionId, turnId, c.epoch);
            if (!eligibleController(sessions.demoState(c.sessionId, c.learnerId), c)) return;
            send(c, "PROCESSING_ERROR", turnId, Map.of("message", "Assessment is unavailable",
                    "snapshot", sessions.socketSnapshot(c.sessionId, c.learnerId)));
        }
    }

    private void deliverDemoReply(Connection c, String turnId, String reply) {
        if (!eligibleController(sessions.demoState(c.sessionId, c.learnerId), c)) return;
        Instant replyStarted = Instant.now();
        try {
            byte[] spoken = tts.synthesize(reply);
            if (spoken.length == 0) throw new IllegalStateException("Cartesia returned empty audio");
            if (!eligibleController(sessions.demoState(c.sessionId, c.learnerId), c)) return;
            sessions.saveAudio(c.sessionId, turnId, SpeakingAudioAsset.Kind.AI_REPLY,
                    "mp3", "audio/mpeg", spoken);
            sessions.recordUsage(c.sessionId, turnId, SpeakingUsageSegment.Kind.AI_REPLY,
                    replyStarted, Instant.now());
            send(c, "AI_AUDIO_READY", turnId, Map.of());
        } catch (Exception ex) {
            log.warn("AI audio failed for session {} turn {}", c.sessionId, turnId, ex);
            if (eligibleController(sessions.demoState(c.sessionId, c.learnerId), c)) {
                send(c, "TTS_ERROR", turnId, Map.of("message", "AI audio is unavailable"));
            }
        }
    }

    private boolean eligibleController(SpeakingSession session, Connection connection) {
        return connectionIsCurrent(connection) && session != null && session.isOpen()
                && session.getClosingAt() == null
                && session.getControllerEpoch() == connection.epoch;
    }

    public void close(String connectionId) {
        Connection c = connections.get(connectionId);
        if (c == null) return;
        boolean closeSpeechStream;
        boolean scheduleCleanup;
        String pendingTurnId;
        synchronized (controllerLock(c.sessionId)) {
            if (!connections.remove(connectionId, c)) return;
            c.active = false;
            boolean wasCurrent = currentControllers.remove(c.sessionId, c);
            purgeInactiveConnections(c.sessionId);
            boolean hasActivePeer = hasActiveConnection(c.sessionId);
            closeSpeechStream = wasCurrent && !hasActivePeer;
            scheduleCleanup = wasCurrent;
            pendingTurnId = c.processing ? c.turnId : null;
        }
        recoverClosedTurn(c, pendingTurnId);
        if (closeSpeechStream) stt.close(c.sessionId);
        if (scheduleCleanup) {
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() ->
                    cleanupDisconnectedSession(c.sessionId, c.learnerId, c.epoch));
        }
    }

    void cleanupDisconnectedSession(String sessionId, String learnerId, long epoch) {
        boolean disconnected;
        synchronized (controllerLock(sessionId)) {
            purgeInactiveConnections(sessionId);
            Connection current = currentControllers.get(sessionId);
            if (current != null && !current.active) currentControllers.remove(sessionId, current);
            disconnected = !hasActiveConnection(sessionId);
            if (disconnected) currentControllers.remove(sessionId);
        }
        if (disconnected) sessions.interruptIfDisconnected(sessionId, learnerId, epoch);
    }

    private Object controllerLock(String sessionId) {
        return controllerLocks.computeIfAbsent(sessionId, ignored -> new Object());
    }

    private boolean connectionIsCurrent(Connection connection) {
        return connection.active && currentControllers.get(connection.sessionId) == connection;
    }

    private boolean hasActiveConnection(String sessionId) {
        return connections.values().stream()
                .anyMatch(connection -> connection.active && connection.sessionId.equals(sessionId));
    }

    private void purgeInactiveConnections(String sessionId) {
        connections.entrySet().removeIf(entry -> entry.getValue().sessionId.equals(sessionId)
                && !entry.getValue().active);
    }

    @EventListener
    public void onSessionEnded(SpeakingSessionService.SessionEndedEvent event) {
        workers.execute(() -> connections.values().stream()
                .filter(connection -> connection.active && connection.sessionId.equals(event.sessionId())
                        && connection.learnerId.equals(event.learnerId()))
                .forEach(connection -> send(connection, "SESSION_ENDED", null, Map.of())));
    }

    private Connection require(String connectionId) {
        Connection c = connections.get(connectionId);
        if (c == null || !c.active) throw new IllegalStateException("Socket no longer controls this session");
        return c;
    }

    private void send(Connection c, String type, String turnId, Map<String, Object> extra) {
        SpeakingSessionResponse current;
        try { current = sessions.socketSnapshot(c.sessionId, c.learnerId); }
        catch (fit.iuh.engolearn.speaking.exception.SpeakingException ex) { return; }
        synchronized (controllerLock(c.sessionId)) {
            sendLocked(c, type, turnId, extra, current);
        }
    }

    private void sendLocked(Connection c, String type, String turnId, Map<String, Object> extra,
                            SpeakingSessionResponse current) {
        if (!connectionIsCurrent(c)) return;
        if (current.controllerEpoch() != c.epoch) return;
        Map<String, Object> event = new java.util.LinkedHashMap<>(extra);
        event.put("type", type);
        event.put("turnId", turnId);
        event.put("controllerEpoch", current.controllerEpoch());
        event.put("revision", current.revision());
        c.emit.accept(event);
    }

    private void recoverReplacedTurn(Connection previous) {
        if (!demoManagerEnabled || previous == null || previous.turnId == null || !previous.processing) return;
        recoverDemoTurn(previous, previous.turnId);
    }

    private void recoverClosedTurn(Connection connection, String turnId) {
        if (!demoManagerEnabled || turnId == null) return;
        recoverDemoTurn(connection, turnId);
    }

    private void recoverDemoTurn(Connection connection, String turnId) {
        try {
            SpeakingSession current = sessions.demoState(connection.sessionId, connection.learnerId);
            if (current != null) {
                sessions.recoverDemoTurn(connection.sessionId, turnId, current.getControllerEpoch());
            }
        } catch (Exception ex) {
            log.warn("Unable to recover demo turn {} for session {}", turnId, connection.sessionId, ex);
        }
    }

    private Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }

    private byte[] wav(byte[] pcm) {
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(16).putShort((short) 1).putShort((short) 1).putInt(16000)
                .putInt(32000).putShort((short) 2).putShort((short) 16);
        header.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(pcm.length);
        byte[] wav = new byte[44 + pcm.length];
        System.arraycopy(header.array(), 0, wav, 0, 44);
        System.arraycopy(pcm, 0, wav, 44, pcm.length);
        return wav;
    }
}
