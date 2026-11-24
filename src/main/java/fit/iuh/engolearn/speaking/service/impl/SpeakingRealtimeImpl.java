package fit.iuh.engolearn.speaking.service.impl;

import fit.iuh.engolearn.speaking.dto.response.SpeakingResponses.*;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.mapper.SpeakingSessionMapper;
import fit.iuh.engolearn.speaking.model.enums.*;
import fit.iuh.engolearn.speaking.model.vo.SpeakingOpeningVo;
import fit.iuh.engolearn.speaking.repository.SpeakingSessionRepository;
import fit.iuh.engolearn.speaking.repository.SpeakingTurnArchive;
import fit.iuh.engolearn.speaking.service.*;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime.Lease;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime.TurnWork;
import fit.iuh.engolearn.speaking.utils.*;
import fit.iuh.engolearn.speaking.utils.port.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class SpeakingRealtimeImpl implements SpeakingRealtime {
    private static final int MAX_STT_TEXT = 8000;
    private static final int MAX_STT_UTF8 = 14000;
    private final SpeakingRuntime runtime;
    private final SpeakingQuota quota;
    private final SpeakingConversation conversation;
    private final ConversationGenerationPort generation;
    private final SpeechStreamPort speech;
    private final SpeechSynthesisPort synthesis;
    private final SpeakingAudioStorage storage;
    private final SpeakingSessionRepository sessions;
    private final SpeakingTurnArchive archive;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SpeakingProviderProperties.Realtime settings;
    private final ThreadPoolExecutor executor;
    private final Semaphore capacity;
    private final Semaphore connections;
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final Map<String, String> workingTurns = new ConcurrentHashMap<>();
    private final Set<String> openingRequests = ConcurrentHashMap.newKeySet();

    public SpeakingRealtimeImpl(SpeakingRuntime runtime, SpeakingQuota quota, SpeakingConversation conversation,
                                ConversationGenerationPort generation, SpeechStreamPort speech, SpeechSynthesisPort synthesis,
                                SpeakingAudioStorage storage, SpeakingSessionRepository sessions, SpeakingTurnArchive archive,
                                ApplicationEventPublisher events, Clock clock, SpeakingProviderProperties providers) {
        this.runtime = runtime;
        this.quota = quota;
        this.conversation = conversation;
        this.generation = generation;
        this.speech = speech;
        this.synthesis = synthesis;
        this.storage = storage;
        this.sessions = sessions;
        this.archive = archive;
        this.events = events;
        this.clock = clock;
        this.settings = providers.getRealtime();
        if (settings.getMaxConnections() < 1 || settings.getMaxFramesPerSecond() < 1 || settings.getMaxCommandsPerSecond() < 1) {
            throw new IllegalArgumentException("Speaking connection and frame limits must be positive");
        }
        this.executor = new ThreadPoolExecutor(settings.getWorkers(), settings.getWorkers(), 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.getQueueCapacity()), Thread.ofPlatform().name("speaking-work-", 0).factory());
        this.capacity = new Semaphore(Math.addExact(settings.getWorkers(), settings.getQueueCapacity()));
        this.connections = new Semaphore(settings.getMaxConnections());
    }

    @Override
    public Connection connect(Lease lease, Channel channel) {
        if (!connections.tryAcquire()) {
            runtime.releaseLease(lease);
            throw busy();
        }
        Peer peer = new Peer(lease, channel);
        var previous = new AtomicReference<Peer>();
        try {
            synchronized (peer.output) {
                peers.compute(lease.sessionId(), (id, old) -> {
                    runtime.state(lease);
                    previous.set(old);
                    return peer;
                });
                if (!peer.send("SESSION_STATE", null, null, null, runtime.state(lease))) {
                    throw new SpeakingException(HttpStatus.CONFLICT, "CONTROLLER_CHANGED", "The Speaking controller changed", false);
                }
            }
            if (previous.get() != null) previous.get().close();
            openSession(lease.sessionId());
            return peer;
        } catch (RuntimeException failure) {
            if (previous.get() != null) previous.get().close();
            peer.close();
            throw failure;
        }
    }

    @Override
    public void openSession(String id) {
        if (!openingRequests.add(id)) return;
        if (!capacity.tryAcquire()) {
            openingRequests.remove(id);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    opening(id);
                } catch (RuntimeException failure) {
                    log.warn("Speaking opening work deferred for session {}", id);
                } finally {
                    openingRequests.remove(id);
                    capacity.release();
                }
            });
        } catch (RejectedExecutionException unavailable) {
            openingRequests.remove(id);
            capacity.release();
        }
    }

    private void opening(String id) {
        var session = runtime.runtimeSession(id);
        if (session.getStatus() != SpeakingSessionStatus.INITIALIZING) return;
        String token = runtime.claimOpening(id, clock.instant().plusSeconds(45));
        if (token == null) return;
        var snapshot = session.getSnapshot();
        SpeakingConversation.Result result = generate(conversation.opening(snapshot), null);
        var opening = SpeakingOpeningVo.builder().reply(result == null ? snapshot.getFallbackReply() : result.reply())
                .hint(result == null || result.hint() == null ? snapshot.getFallbackHint() : result.hint())
                .audioStatus(SpeakingAudioStatus.AUDIO_PENDING).build();
        if (!runtime.completeOpening(id, token, opening)) return;
        var persisted = SpeakingSessionMapper.session(runtime.runtimeSession(id), clock.instant());
        Peer peer = peers.get(id);
        boolean textSent = peer != null && peer.send("OPENING_RESULT", null, null, null, persisted.opening());
        byte[] wav = synthesize(opening.getReply());
        var media = wav == null ? null : storage.store("speaking-opening", id, wav);
        var audioStatus = wav == null ? SpeakingAudioStatus.AUDIO_FAILED : SpeakingAudioStatus.AUDIO_READY;
        if (!runtime.openingAudio(id, token, audioStatus, media)) return;
        if (textSent) {
            if (wav == null) peer.ttsFailed(null, null, null);
            else peer.wav(null, null, null, wav, () -> runtime.runtimeSession(id).getLastTurnSequence() == 0);
        }
    }

    private SpeakingConversation.Result generate(SpeakingConversation.Prompt prompt, String transcript) {
        try {
            return conversation.parse(generation.generate(prompt), transcript);
        } catch (RuntimeException providerUnavailable) {
            return null;
        }
    }

    private byte[] synthesize(String reply) {
        try {
            byte[] wav = synthesis.synthesize(reply);
            SpeakingWav.durationMs(wav);
            return wav;
        } catch (RuntimeException providerUnavailable) {
            return null;
        }
    }

    private void submit(TurnWork work, int sequence, Runnable action) {
        String key = work.sessionId();
        workingTurns.put(key, work.token());
        try {
            executor.execute(() -> {
                try {
                    action.run();
                } catch (SpeakingException stale) {
                    if (!Set.of("STALE_TURN", "SESSION_NOT_FOUND", "CONTROLLER_CHANGED").contains(stale.code())) {
                        failSafely(work, sequence);
                    }
                } catch (RuntimeException failure) {
                    log.warn("Speaking work deferred for turn {}", work.turnId());
                    failSafely(work, sequence);
                } finally {
                    workingTurns.remove(key, work.token());
                    capacity.release();
                    recordQuota(key);
                    settleConnection(key);
                }
            });
        } catch (RejectedExecutionException failure) {
            workingTurns.remove(key, work.token());
            capacity.release();
            failSafely(work, sequence);
        }
    }

    private void failSafely(TurnWork work, int sequence) {
        try {
            String code = runtime.workState(work).getTranscript() == null ? "STT_UNAVAILABLE" : "CONVERSATION_UNAVAILABLE";
            fail(work, sequence, code);
        } catch (RuntimeException deferred) {
            log.warn("Speaking turn {} will be checked by recovery", work.turnId());
        }
    }

    private void transcribe(TurnWork work, int sequence, byte[] pcm,
                            CompletableFuture<SpeakingRecording.TimedTranscript> pending, SpeakingRecording recording) {
        try {
            runtime.finalizedAudioEvidence(work.sessionId(), work.turnId());
            var ref = pcm.length == 0 ? null : storage.store("speaking-learner", work.turnId(), SpeakingWav.learner(pcm));
            try {
                runtime.attachAudio(work, ref);
            } finally {
                try {
                    events.publishEvent(new FinalizedSpeakingAudio(work.sessionId(), work.turnId(), sequence,
                            ref == null ? null : ref.getMediaId(), pcm));
                } catch (RuntimeException unavailable) {
                    log.warn("Speaking audio listener failed for turn {}", work.turnId());
                }
            }
            if (pending == null) {
                fail(work, sequence, "EMPTY_AUDIO");
                return;
            }
            SpeakingRecording.TimedTranscript transcript;
            try {
                transcript = pending.join();
            } catch (CompletionException unavailable) {
                fail(work, sequence, "STT_UNAVAILABLE");
                return;
            }
            if (transcript.original() == null || transcript.original().codePoints().noneMatch(Character::isLetterOrDigit)) {
                fail(work, sequence, "EMPTY_AUDIO");
                return;
            }
            if (!validTranscriptSize(transcript.original())) {
                fail(work, sequence, "STT_UNAVAILABLE");
                return;
            }
            runtime.transcript(work, transcript.original(), transcript.waitMs());
            publish(work, sequence, "TRANSCRIPT_FINAL", Map.of("transcript", transcript.original()));
            publish(work, sequence, "TURN_PROCESSING", Map.of("status", SpeakingTurnStatus.GENERATING));
        } finally {
            recording.close();
        }
        generateTurn(work, sequence);
    }

    private boolean validTranscriptSize(String text) {
        return text.length() <= MAX_STT_TEXT && text.getBytes(StandardCharsets.UTF_8).length <= MAX_STT_UTF8;
    }

    private void generateTurn(TurnWork work, int sequence) {
        var turn = runtime.workState(work);
        var session = runtime.runtimeSession(work.sessionId());
        var history = runtime.conversationHistory(work);
        var memory = SpeakingConversation.replay(session.getMemory(), history);
        var prompt = conversation.context(session.getSnapshot(), session.getOpening().getReply(), memory,
                history, turn.getTranscript(), turn.getSpeechDurationMs());
        long start = System.nanoTime();
        var response = generate(prompt, turn.getTranscript());
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (response == null) {
            fail(work, sequence, "CONVERSATION_UNAVAILABLE");
            return;
        }

        Turn committed = runtime.commit(work, response, elapsed);
        var payload = new TurnResult(committed.reply(), committed.hint(), committed.correction());
        Peer peer = peers.get(work.sessionId());
        boolean textSent = peer != null && peer.send("TURN_RESULT", null, work.turnId(), sequence, payload);
        if (textSent) archive.markReplyDelivered(work.sessionId(), work.turnId(), work.token(), clock.instant());
        audioReply(work, sequence, committed.reply(), textSent ? peer : null);
    }

    private void audioReply(TurnWork work, int sequence, String reply, Peer peer) {
        BooleanSupplier current = () -> archive.deliveryCurrent(work.sessionId(), work.turnId(), work.token(), clock.instant());
        if (!current.getAsBoolean()) return;
        byte[] wav = synthesize(reply);
        if (!current.getAsBoolean()) return;
        runtime.runtimeSession(work.sessionId());
        var media = wav == null ? null : storage.store("speaking-reply", work.turnId(), wav);
        runtime.runtimeSession(work.sessionId());
        if (media != null) archive.stageAssistantAudio(work.sessionId(), work.turnId(), work.token(), media, clock.instant());
        boolean delivered = false;
        if (peer != null && current.getAsBoolean()) {
            if (wav == null) peer.ttsFailed(null, work.turnId(), sequence);
            else delivered = peer.wav(null, work.turnId(), sequence, wav, current);
        }
        archive.finishAudio(work.sessionId(), work.turnId(), work.token(),
                wav == null ? SpeakingAudioStatus.AUDIO_FAILED : SpeakingAudioStatus.AUDIO_READY, media,
                delivered ? SpeakingWav.durationMs(wav) : 0, clock.instant(), false);
    }

    private record TurnResult(String reply, Hint hint, Correction correction) {}

    private void fail(TurnWork work, int sequence, String code) {
        var previous = runtime.workState(work);
        boolean empty = "EMPTY_AUDIO".equals(code);
        boolean retryable = "CONVERSATION_UNAVAILABLE".equals(code) && previous.getAttempt() == 0
                && runtime.runtimeSession(work.sessionId()).getStatus() == SpeakingSessionStatus.ACTIVE;
        runtime.settle(work, empty ? SpeakingTurnStatus.SKIPPED : SpeakingTurnStatus.FAILED, code, null, null, null);
        publish(work, sequence, empty ? "TURN_SKIPPED" : "TURN_FAILED",
                new ErrorPayload(code, retryable, switch (code) {
                    case "EMPTY_AUDIO" -> "No speech was detected";
                    case "STT_UNAVAILABLE" -> "Speech recognition is unavailable";
                    default -> "The conversation reply is unavailable";
                }, null));
    }

    private void recordQuota(String sessionId) {
        try {
            quota.pending(sessionId);
        } catch (RuntimeException unavailable) {
            log.warn("Speaking charge will be retried for session {}", sessionId);
        }
    }

    private boolean publish(TurnWork work, int sequence, String type, Object payload) {
        Peer peer = peers.get(work.sessionId());
        return peer != null && peer.send(type, null, work.turnId(), sequence, payload);
    }

    @Override
    public void recover() {
        for (var session : sessions.findByStatusInAndDeletedAtIsNull(List.of(SpeakingSessionStatus.INITIALIZING))) {
            openSession(session.getId());
        }
        for (var session : sessions.findByOpeningAudioStatusAndOpeningDeadlineLessThanEqualAndDeletedAtIsNull(
                SpeakingAudioStatus.AUDIO_PENDING, clock.instant())) {
            runtime.openingAudio(session.getId(), session.getOpeningToken(), SpeakingAudioStatus.AUDIO_FAILED, null);
        }
        for (String id : peers.keySet()) settleConnection(id);
    }

    @Override
    public void sessionChanged(String sessionId) {
        settleConnection(sessionId);
    }

    private void settleConnection(String id) {
        Peer peer = peers.get(id);
        if (peer == null) return;
        try {
            Session current = runtime.state(peer.lease);
            if (current.status() == SpeakingSessionStatus.ENDED && !workingTurns.containsKey(id)) {
                peer.send("SESSION_ENDED", null, null, null, current);
                peer.close();
            } else if (current.status() == SpeakingSessionStatus.CLOSING) {
                peer.stopReceiving();
            }
        } catch (SpeakingException stale) {
            peer.close();
        }
    }

    private SpeakingException busy() {
        return new SpeakingException(HttpStatus.SERVICE_UNAVAILABLE, "REQUEST_CONFLICT",
                "Speaking is busy. Try again shortly.", true);
    }

    @PreDestroy
    void shutdown() {
        peers.values().forEach(Peer::close);
        executor.shutdownNow();
    }

    private final class Peer implements Connection {
        private final Lease lease;
        private final Channel channel;
        private final Object output = new Object();
        private SpeakingRecording recording;
        private int recordingSequence;
        private volatile long lastPartialNanos;
        private volatile boolean closed;
        private long rateSecond;
        private int commands;
        private int frames;

        private Peer(Lease lease, Channel channel) {
            this.lease = lease;
            this.channel = channel;
        }

        @Override
        public synchronized void command(Command command) {
            if (!allow(true)) return;
            try {
                switch (command.type()) {
                    case START_TURN -> start(command);
                    case END_TURN -> end(command);
                    case CANCEL_TURN -> cancel(command);
                    case RETRY_TURN -> retry(command);
                    case SYNC_SESSION -> send("SESSION_STATE", command.requestId(), null, null, runtime.state(lease));
                }
            } catch (SpeakingException rejected) {
                send("ERROR", command.requestId(), command.turnId() == null ? null : command.turnId().toString(),
                        null, new ErrorPayload(rejected.code(), rejected.retryable(), rejected.getMessage(), rejected.activeSessionId()));
                if ("CONTROLLER_CHANGED".equals(rejected.code())) close();
                if (Set.of("SESSION_ENDED", "DAILY_QUOTA_REACHED").contains(rejected.code())) settleConnection(lease.sessionId());
            } catch (RuntimeException unavailable) {
                send("ERROR", command.requestId(), null, null, new ErrorPayload("REQUEST_CONFLICT", true,
                        "Speaking is temporarily unavailable. Try again shortly.", null));
            }
        }

        private void start(Command command) {
            if (recording != null) {
                var same = runtime.admit(lease, command.clientTurnId());
                accepted(command, same);
                return;
            }
            if (workingTurns.containsKey(lease.sessionId())) throw new SpeakingException(HttpStatus.CONFLICT,
                    "TURN_IN_PROGRESS", "A turn is already in progress", true);
            if (!capacity.tryAcquire()) throw busy();
            boolean retained = false;
            try {
                runtime.closeRetryWindow(lease, command.clientTurnId());
                quota.pending(lease.sessionId());
                if (archive.hasPendingAudio(lease.sessionId())) throw new SpeakingException(HttpStatus.CONFLICT,
                        "TURN_IN_PROGRESS", "The preceding turn audio is still processing", true);
                Turn accepted = runtime.admit(lease, command.clientTurnId());
                if (accepted.status() != SpeakingTurnStatus.RECEIVING) {
                    send("SESSION_STATE", command.requestId(), null, null, runtime.state(lease));
                    return;
                }
                if (!accepted(command, accepted)) {
                    runtime.cancel(lease, accepted.turnId());
                    return;
                }
                recordingSequence = accepted.sequence();
                lastPartialNanos = 0;
                recording = new SpeakingRecording(speech, accepted.turnId(), text -> {
                    long instant = System.nanoTime();
                    if (instant - lastPartialNanos >= TimeUnit.MILLISECONDS.toNanos(100)
                            && text != null && !text.isBlank() && validTranscriptSize(text)) {
                        lastPartialNanos = instant;
                        send("TRANSCRIPT_PARTIAL", null, accepted.turnId(), accepted.sequence(), Map.of("transcript", text));
                    }
                });
                retained = true;
            } finally {
                if (!retained) capacity.release();
            }
        }

        private boolean accepted(Command command, Turn turn) {
            if (turn.status() != SpeakingTurnStatus.RECEIVING) {
                return send("SESSION_STATE", command.requestId(), null, null, runtime.state(lease));
            }
            return send("TURN_ACCEPTED", command.requestId(), turn.turnId(), turn.sequence(),
                    Map.of("clientTurnId", turn.clientTurnId()));
        }

        @Override
        public synchronized void audio(SpeakingFrames.Learner frame) {
            if (!allow(false)) return;
            try {
                var state = runtime.state(lease);
                if (state.status() != SpeakingSessionStatus.ACTIVE || recording == null || state.activeTurn() == null
                        || !recording.turnId().equals(state.activeTurn().turnId())) throw stale();
                recording.add(frame, state.limits().maxTurnAudioMs(), () -> runtime.recordingStarted(lease, recording.turnId()));
            } catch (SpeakingException rejected) {
                error(null, rejected.code(), rejected.getMessage());
                if ("CONTROLLER_CHANGED".equals(rejected.code())) close();
            } catch (IllegalArgumentException invalid) {
                error(null, "INVALID_FRAME", "Invalid learner audio sequence");
            } catch (IllegalStateException tooLong) {
                error(null, "AUDIO_LIMIT", "The turn audio exceeds its limit");
            }
        }

        private void end(Command command) {
            var current = receiving(command.turnId());
            byte[] pcm = current.pcm();
            var work = runtime.seal(lease, current.turnId(), null, SpeakingRecording.speechDurationMs(pcm));
            int sequence = recordingSequence;
            recording = null;
            var pending = SpeakingWav.containsSignal(pcm) ? current.finish() : null;
            send("TURN_PROCESSING", command.requestId(), work.turnId(), sequence,
                    Map.of("status", SpeakingTurnStatus.TRANSCRIBING));
            submit(work, sequence, () -> transcribe(work, sequence, pcm, pending, current));
        }

        private void cancel(Command command) {
            var current = receiving(command.turnId());
            var turn = runtime.cancel(lease, current.turnId());
            stopReceiving();
            send("TURN_SKIPPED", command.requestId(), turn.turnId(), turn.sequence(),
                    new ErrorPayload("TURN_CANCELLED", false, "Recording cancelled", null));
        }

        private void retry(Command command) {
            if (!capacity.tryAcquire()) throw busy();
            TurnWork work;
            try {
                work = runtime.retry(lease, command.turnId().toString());
            } catch (RuntimeException failure) {
                capacity.release();
                throw failure;
            }
            int sequence = runtime.workState(work).getSequence();
            send("TURN_PROCESSING", command.requestId(), work.turnId(), sequence,
                    Map.of("status", SpeakingTurnStatus.GENERATING));
            submit(work, sequence, () -> generateTurn(work, sequence));
        }

        private SpeakingRecording receiving(UUID id) {
            if (recording == null || !recording.turnId().equals(id.toString())) throw stale();
            return recording;
        }

        private SpeakingException stale() {
            return new SpeakingException(HttpStatus.CONFLICT, "STALE_TURN", "The turn is no longer recording", false);
        }

        private boolean allow(boolean command) {
            if (closed) return false;
            long second = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime());
            if (second != rateSecond) {
                rateSecond = second;
                commands = 0;
                frames = 0;
            }
            boolean accepted = command ? ++commands <= settings.getMaxCommandsPerSecond()
                    : ++frames <= settings.getMaxFramesPerSecond();
            if (!accepted) {
                send("ERROR", null, null, null, new ErrorPayload("RATE_LIMIT", true, "Speaking frame rate exceeded", null));
                close();
            }
            return accepted;
        }

        @Override
        public synchronized void error(UUID requestId, String code, String message) {
            if (!allow(true)) return;
            send("ERROR", requestId, null, null, new ErrorPayload(code, false, message, null));
        }

        private boolean send(String type, UUID request, String turn, Integer sequence, Object payload) {
            synchronized (output) {
                try {
                    if (closed) return false;
                    Session session = runtime.state(lease);
                    if ("TRANSCRIPT_PARTIAL".equals(type) && (session.activeTurn() == null
                            || !session.activeTurn().turnId().equals(turn)
                            || session.activeTurn().status() != SpeakingTurnStatus.RECEIVING)) return false;
                    channel.send(new Event(type, lease.sessionId(), request, turn, sequence, payload));
                    return true;
                } catch (IOException | RuntimeException unavailable) {
                    return false;
                }
            }
        }

        private boolean wav(UUID request, String turn, Integer sequence, byte[] wav, BooleanSupplier current) {
            synchronized (output) {
                if (!current.getAsBoolean() || !send("TTS_STARTED", request, turn, sequence, Map.of("mimeType", "audio/wav"))) return false;
                UUID turnId = turn == null ? null : UUID.fromString(turn);
                try {
                    for (int start = 0, index = 0; start < wav.length; start += SpeakingFrames.CHUNK, index++) {
                        if (!current.getAsBoolean()) return false;
                        runtime.state(lease);
                        channel.send(SpeakingFrames.assistant(turnId, index, wav, start,
                                Math.min(SpeakingFrames.CHUNK, wav.length - start)));
                    }
                    return send("TTS_COMPLETED", request, turn, sequence, Map.of("byteLength", wav.length));
                } catch (IOException | RuntimeException unavailable) {
                    ttsFailed(request, turn, sequence);
                    return false;
                }
            }
        }

        private void ttsFailed(UUID request, String turn, Integer sequence) {
            send("TTS_FAILED", request, turn, sequence, Map.of("code", "TTS_UNAVAILABLE", "retryable", false));
        }

        private synchronized void stopReceiving() {
            if (recording == null) return;
            var previous = recording;
            recording = null;
            try {
                previous.close();
            } finally {
                capacity.release();
            }
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            peers.remove(lease.sessionId(), this);
            try {
                stopReceiving();
                try {
                    runtime.releaseLease(lease);
                } catch (SpeakingException released) {
                    if (!"SESSION_NOT_FOUND".equals(released.code())) throw released;
                }
            } finally {
                connections.release();
                channel.close();
            }
        }
    }
}
