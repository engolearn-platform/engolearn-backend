package fit.iuh.engolearn.speaking.service.impl;

import fit.iuh.engolearn.media.contract.MediaManagement;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.model.SpeakingAudioAssessment;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.model.SpeakingTurn;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAssessmentStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioAssessmentStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingCriterionStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingSessionStatus;
import fit.iuh.engolearn.speaking.model.vo.*;
import fit.iuh.engolearn.speaking.repository.SpeakingAssessmentRepository;
import fit.iuh.engolearn.speaking.repository.SpeakingTurnRepository;
import fit.iuh.engolearn.speaking.service.FinalizedSpeakingAudio;
import fit.iuh.engolearn.speaking.service.SpeakingAssessment;
import fit.iuh.engolearn.speaking.service.SpeakingRuntime;
import fit.iuh.engolearn.speaking.utils.*;
import fit.iuh.engolearn.speaking.utils.port.AcousticAssessmentPort;
import fit.iuh.engolearn.speaking.utils.port.AcousticAssessmentPort.Failure;
import fit.iuh.engolearn.speaking.utils.port.LanguageAssessmentPort;
import fit.iuh.engolearn.speaking.utils.port.LearnerAssessmentAudioPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static fit.iuh.engolearn.speaking.model.enums.SpeakingAudioAssessmentStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakingAssessmentImpl implements SpeakingAssessment {
    private static final int RECOVERY_BATCH = 32;
    private static final long MAX_RECOVERY_AUDIO_MS = 120_000;
    private final SpeakingAssessmentRepository repository;
    private final SpeakingTurnRepository turns;
    private final SpeakingRuntime runtime;
    private final SpeakingProviderProperties properties;
    private final AcousticAssessmentPort acoustic;
    private final LanguageAssessmentPort language;
    private final MediaManagement media;
    private final LearnerAssessmentAudioPort retainedAudio;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<String, FutureTask<Void>> queuedAudio = new ConcurrentHashMap<>();
    private final Map<String, FutureTask<Void>> queuedLanguage = new ConcurrentHashMap<>();
    private final String instance = UUID.randomUUID().toString();
    private ThreadPoolExecutor audioExecutor;
    private ThreadPoolExecutor languageExecutor;
    private String recoveryCursor;

    @PostConstruct
    void startWorkers() {
        var settings = properties.getAssessment();
        if (settings.getAudioTransientRetries() < 0 || settings.getAudioTransientRetries() > 5
                || settings.getAudioWorkers() < 1 || settings.getAudioWorkers() > 16
                || settings.getAudioQueueCapacity() < 1 || settings.getAudioQueueCapacity() > 100
                || settings.getAudioTimeout().toMillis() < 1 || settings.getAudioDeadline().toMillis() < 1
                || settings.getLanguageTimeout().toMillis() < 1) {
            throw new IllegalArgumentException("Invalid Speaking assessment work budget");
        }
        audioExecutor = workers("speaking-acoustic-", settings.getAudioWorkers(), settings.getAudioQueueCapacity());
        languageExecutor = workers("speaking-rubric-", 1, 50);
    }

    private static ThreadPoolExecutor workers(String name, int workers, int capacity) {
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity),
                Thread.ofPlatform().daemon(true).name(name, 1).factory());
    }

    @PreDestroy
    void stopWorkers() {
        audioExecutor.shutdownNow();
        languageExecutor.shutdownNow();
    }

    @EventListener
    public void audioSealed(FinalizedSpeakingAudio event) {
        var session = liveSession(event.sessionId());
        if (session == null || audioClosed(session, clock.instant())) return;
        var evidence = runtime.finalizedAudioEvidence(event.sessionId(), event.turnId());
        if (evidence.getSequence() != event.sequence() || !sealedWithinCutoff(session, evidence)) return;
        if (register(session.getId(), evidence).getStatus() != PENDING) return;
        if (Boolean.FALSE.equals(evidence.getCaptureComplete())) {
            rejectCapture(session.getId(), event.turnId(), "AUDIO_INCOMPLETE");
            return;
        }
        if (event.pcmLength() > maximumPcmBytes(session)) {
            rejectCapture(session.getId(), event.turnId(), "AUDIO_LENGTH_UNSUPPORTED");
            return;
        }
        enqueueAudio(session.getId(), evidence.getTurnId(), event.pcm());
    }

    @Override
    public synchronized void recover() {
        var sessions = repository.workAfter(recoveryCursor, RECOVERY_BATCH);
        recoveryCursor = sessions.size() < RECOVERY_BATCH ? null : sessions.getLast().getId();
        for (var session : sessions) {
            try {
                reconcile(session);
            } catch (RuntimeException failure) {
                log.warn("Speaking assessment will be retried for session {}", session.getId());
            }
        }
    }

    private void reconcile(SpeakingSession session) {
        Instant now = clock.instant();
        var history = history(session);
        var samples = repository.samples(session.getId());
        boolean closing = session.getStatus() == SpeakingSessionStatus.ENDED;
        boolean expired = audioClosed(session, now);
        var existing = new HashMap<String, SpeakingAudioAssessment>();
        for (var sample : samples) existing.put(sample.getId(), sample);
        for (var turn : history) {
            recoverAudio(session, now, expired, existing.get(turn.getTurnId()), turn);
        }
        if (!closing) return;

        if (session.getLanguageAssessment() == null) {
            recoverLanguage(session, history, now);
        }
        session = liveSession(session.getId());
        if (session == null || session.getLanguageAssessment() == null) return;

        Instant audioDeadline = audioDeadline(session);
        var settled = new HashMap<String, SpeakingAudioAssessment>();
        for (var sample : repository.samples(session.getId())) {
            if (sample.getStatus().terminal() && sample.getFinishedAt() != null && !sample.getFinishedAt().isAfter(audioDeadline)) {
                settled.put(sample.getId(), sample);
            }
        }
        boolean incomplete = history.stream().anyMatch(turn -> !settled.containsKey(turn.getTurnId()));
        if (!incomplete || !clock.instant().isBefore(audioDeadline)) {
            var ordered = history.stream().map(turn -> settled.get(turn.getTurnId())).filter(Objects::nonNull).toList();
            publish(session, ordered, incomplete);
        }
    }

    private void recoverAudio(SpeakingSession session, Instant now, boolean expired, SpeakingAudioAssessment sample, SpeakingTurnVo turn) {
        if (sample == null) {
            if (!turn.getStatus().terminal() && turn.getLearnerAudio() == null && !expired) return;
            register(session.getId(), turn);
        } else if (sample.getStatus().terminal()) {
            return;
        } else if (sample.getStatus() == PROCESSING) {
            boolean local = instance.equals(sample.getWorkerInstance());
            if (local && now.isBefore(sample.getClaimDeadline().plusSeconds(1)) && !expired) return;
            repository.recoverClaim(sample, attemptLimit(), now, local ? "AUDIO_PROVIDER_TIMEOUT" : "AUDIO_WORK_INTERRUPTED");
            if (local) cancel(audioExecutor, queuedAudio, turn.getTurnId());
        }
        if (expired) {
            repository.rejectPending(session.getId(), turn.getTurnId(), "AUDIO_DEADLINE_EXCEEDED", now);
            cancel(audioExecutor, queuedAudio, turn.getTurnId());
        } else {
            enqueueAudio(session.getId(), turn.getTurnId(), null);
        }
    }

    private List<SpeakingTurnVo> history(SpeakingSession session) {
        var rows = new TreeMap<Integer, SpeakingTurnVo>();
        for (SpeakingTurn row : turns.findBySessionIdAndValueSealedAtIsNotNullOrderByValueSequenceAsc(session.getId())) {
            var turn = row.getValue();
            if (sealedWithinCutoff(session, turn)) rows.put(turn.getSequence(), turn);
        }
        var active = session.getActiveTurn();
        if (active != null && sealedWithinCutoff(session, active)) rows.put(active.getSequence(), active);
        return List.copyOf(rows.values());
    }

    private static boolean sealedWithinCutoff(SpeakingSession session, SpeakingTurnVo turn) {
        return turn.getSealedAt() != null && turn.getSequence() <= session.getLastTurnSequence()
                && (session.getClosingCutoff() == null || turn.getSealedAt().isBefore(session.getClosingCutoff()));
    }

    private static boolean pendingReply(SpeakingTurnVo turn, Instant now) {
        return turn.getAudioStatus() == SpeakingAudioStatus.AUDIO_PENDING && turn.getReplyDeliveredAt() == null
                && turn.getDeliveryDeadline() != null && turn.getDeliveryDeadline().isAfter(now);
    }

    private SpeakingAudioAssessment register(String sessionId, SpeakingTurnVo turn) {
        return repository.register(sessionId, turn.getTurnId(), turn.getSequence(), clock.instant(),
                SpeakingAcousticPolicy.VERSION, AzureSpeakingAssessment.LOCALE);
    }

    private void rejectCapture(String sessionId, String turnId, String reason) {
        var now = clock.instant();
        var claim = repository.claim(sessionId, turnId, instance, UUID.randomUUID().toString(), now,
                now.plus(properties.getAssessment().getAudioTimeout()), attemptLimit());
        if (claim != null) finishAudio(claim, SKIPPED, reason, now);
    }

    private void enqueueAudio(String sessionId, String turnId, byte[] pcm) {
        boolean admitted = enqueue(audioExecutor, queuedAudio, turnId, () -> {
            try {
                evaluateAudio(sessionId, turnId, pcm);
            } catch (RuntimeException failure) {
                log.warn("Speaking acoustic evidence will be checked by recovery for turn {}", turnId);
            }
        });
        if (!admitted) repository.rejectPending(sessionId, turnId, "AUDIO_QUEUE_FULL", clock.instant());
    }

    private static boolean enqueue(ThreadPoolExecutor executor, Map<String, FutureTask<Void>> jobs, String id, Runnable work) {
        var task = new FutureTask<>(work, (Void) null) {
            @Override protected void done() {
                jobs.remove(id, this);
            }
        };
        if (jobs.putIfAbsent(id, task) != null) return true;
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException rejected) {
            jobs.remove(id, task);
            return false;
        }
    }

    private static void cancel(ThreadPoolExecutor executor, Map<String, FutureTask<Void>> jobs, String id) {
        var task = jobs.get(id);
        if (task != null && task.cancel(true)) executor.remove(task);
    }

    private void evaluateAudio(String sessionId, String turnId, byte[] livePcm) {
        var config = properties.getAssessment();
        SpeakingAcousticPolicy.Prepared audio = null;
        for (int attempt = 0; attempt < attemptLimit(); attempt++) {
            var session = liveSession(sessionId);
            var now = clock.instant();
            if (session == null || audioClosed(session, now)) return;
            var deadline = earlier(now.plus(config.getAudioTimeout()), audioDeadline(session));
            var work = repository.claim(sessionId, turnId, instance, UUID.randomUUID().toString(), now, deadline, attemptLimit());
            if (work == null) return;
            try {
                var turn = runtime.finalizedAudioEvidence(sessionId, turnId);
                if (!sealedWithinCutoff(session, turn)) throw new Failure("AUDIO_OUTSIDE_CUTOFF", false);
                if (Boolean.FALSE.equals(turn.getCaptureComplete())) {
                    finishAudio(work, SKIPPED, "AUDIO_INCOMPLETE", clock.instant());
                    return;
                }
                byte[] pcm = livePcm;
                if (audio == null) {
                    if (pcm == null) pcm = recoveredPcm(session, turn, remaining(deadline));
                    audio = SpeakingAcousticPolicy.prepare(pcm, session.getLimits().getMaxTurnAudioMs());
                }
                if (audio.reason() != null) {
                    finishAudio(work, SKIPPED, audio.reason(), clock.instant());
                    return;
                }
                var outcome = acoustic.assess(audio.pcm(), remaining(deadline));
                var finished = clock.instant();
                if (!finished.isBefore(deadline)) throw new Failure("AUDIO_PROVIDER_TIMEOUT", true);
                var latest = liveSession(sessionId);
                if (latest == null) return;
                if (audioClosed(latest, finished)) throw new Failure("AUDIO_DEADLINE_EXCEEDED", false);
                var valid = SpeakingAcousticPolicy.sample(audio.voicedMs(), outcome);
                boolean scored = valid.pronunciation() != null || valid.fluency() != null;
                boolean invalid = "INVALID_PROVIDER_RESULT".equals(valid.pronunciationReason())
                        || "INVALID_PROVIDER_RESULT".equals(valid.fluencyReason());
                var status = scored ? ASSESSED : invalid ? FAILED : SKIPPED;
                String reason = scored ? null : invalid ? "INVALID_PROVIDER_RESULT" : "INSUFFICIENT_RECOGNIZED_SPEECH";
                repository.finish(work, status, reason, finished, audio.durationMs(), valid.words(), valid.pronunciation(),
                        valid.pronunciationReason(), valid.fluency(), valid.fluencyReason(), valid.feedback());
                return;
            } catch (Failure unavailable) {
                now = clock.instant();
                if (unavailable.transientFailure() && work.getAttemptCount() < attemptLimit()
                        && now.isBefore(audioDeadline(session)) && !Thread.currentThread().isInterrupted()) {
                    repository.returnToQueue(work, now);
                    continue;
                }
                var status = "AUDIO_LENGTH_UNSUPPORTED".equals(unavailable.getMessage()) ? SKIPPED : FAILED;
                finishAudio(work, status, unavailable.getMessage(), now);
                return;
            } catch (SpeakingException removed) {
                finishAudio(work, FAILED, "AUDIO_EVIDENCE_UNAVAILABLE", clock.instant());
                return;
            } catch (RuntimeException unavailable) {
                finishAudio(work, FAILED, "AUDIO_PROVIDER_UNAVAILABLE", clock.instant());
                return;
            }
        }
    }

    private byte[] recoveredPcm(SpeakingSession session, SpeakingTurnVo turn, Duration timeout) {
        var reference = turn.getLearnerAudio();
        Instant now = clock.instant();
        if (reference == null || reference.getMediaId() == null || reference.getStoredAt() == null
                || !now.isBefore(reference.getStoredAt().plus(SpeakingSessionSettings.AUDIO_RETENTION))) {
            throw new Failure("AUDIO_MEDIA_UNAVAILABLE", false);
        }
        var current = liveSession(session.getId());
        if (current == null || audioClosed(current, now)) throw new Failure("AUDIO_DEADLINE_EXCEEDED", false);
        int maximumBytes = maximumPcmBytes(session);
        try {
            var location = media.resolve(reference.getMediaId());
            if (location.expiresAt() == null || !now.isBefore(location.expiresAt())) {
                throw new Failure("AUDIO_MEDIA_UNAVAILABLE", false);
            }
            var wav = retainedAudio.read(location.url(), maximumBytes + 44, timeout);
            return SpeakingWav.learnerPcm(wav, maximumBytes);
        } catch (MediaNotFoundException missing) {
            throw new Failure("AUDIO_MEDIA_UNAVAILABLE", false);
        } catch (IllegalArgumentException corrupt) {
            throw new Failure("AUDIO_CORRUPT", false);
        } catch (Failure unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new Failure("AUDIO_MEDIA_UNAVAILABLE", true);
        }
    }

    private static int maximumPcmBytes(SpeakingSession session) {
        return (int) Math.min(MAX_RECOVERY_AUDIO_MS, session.getLimits().getMaxTurnAudioMs()) * SpeakingWav.PCM_BYTES_PER_MS;
    }

    private void finishAudio(SpeakingAudioAssessment work, SpeakingAudioAssessmentStatus status, String reason, Instant now) {
        repository.finish(work, status, reason, now, 0, 0, null, reason, null, reason, List.of());
    }

    private void recoverLanguage(SpeakingSession session, List<SpeakingTurnVo> history, Instant now) {
        String sessionId = session.getId();
        Instant cutoff = session.getClosingCutoff() == null ? session.getEndedAt() : session.getClosingCutoff();
        Instant deadline = session.getAssessmentLanguageDeadline();
        if (deadline == null) {
            if (now.isBefore(cutoff) && history.stream().anyMatch(turn -> pendingReply(turn, now))) return;
            var timeout = properties.getAssessment().getLanguageTimeout();
            deadline = earlier(now.plus(timeout), later(session.getEndedAt(), cutoff).plus(timeout));
            if (!repository.startLanguage(sessionId, deadline)) return;
        }
        if (!now.isBefore(deadline)) {
            var evidence = SpeakingLanguageRubric.evidence(mapper, session.getSnapshot(), history, cutoff);
            repository.storeLanguage(sessionId, languageMissing(evidence, "LANGUAGE_PROVIDER_TIMEOUT"));
            cancel(languageExecutor, queuedLanguage, sessionId);
            return;
        }
        if (!enqueueLanguage(sessionId, cutoff, deadline)) {
            var evidence = SpeakingLanguageRubric.evidence(mapper, session.getSnapshot(), history, cutoff);
            repository.storeLanguage(sessionId, languageMissing(evidence, "LANGUAGE_QUEUE_FULL"));
        }
    }

    private boolean enqueueLanguage(String sessionId, Instant cutoff, Instant deadline) {
        return enqueue(languageExecutor, queuedLanguage, sessionId, () -> {
            try {
                evaluateLanguage(sessionId, cutoff, deadline);
            } catch (RuntimeException failure) {
                log.warn("Speaking language evidence will be retried for session {}", sessionId);
            }
        });
    }

    private void evaluateLanguage(String sessionId, Instant cutoff, Instant deadline) {
        var session = liveSession(sessionId);
        if (session == null || session.getLanguageAssessment() != null) return;
        var history = history(session);
        var evidence = SpeakingLanguageRubric.evidence(mapper, session.getSnapshot(), history, cutoff);
        if (!evidence.sufficient() || !clock.instant().isBefore(deadline)) {
            repository.storeLanguage(sessionId, languageMissing(evidence, "LANGUAGE_PROVIDER_TIMEOUT"));
            return;
        }
        var timeout = properties.getAssessment().getLanguageTimeout();
        long started = System.nanoTime();
        SpeakingLanguageAssessmentVo result;
        try {
            String raw = language.assess(evidence.system(), evidence.data(), evidence.messages(), SpeakingLanguageRubric.SCHEMA, timeout);
            result = SpeakingLanguageRubric.parse(mapper, raw, history, evidence.turnCount(), evidence.wordCount());
        } catch (LanguageAssessmentPort.Failure unavailable) {
            result = languageMissing(evidence, unavailable.getMessage());
        } catch (RuntimeException unavailable) {
            result = languageMissing(evidence, "LANGUAGE_PROVIDER_UNAVAILABLE");
        }
        if (!clock.instant().isBefore(deadline) || System.nanoTime() - started >= timeout.toNanos()) {
            result = languageMissing(evidence, "LANGUAGE_PROVIDER_TIMEOUT");
        }
        repository.storeLanguage(sessionId, result);
    }

    private static SpeakingLanguageAssessmentVo languageMissing(SpeakingLanguageRubric.Evidence evidence, String reason) {
        var status = evidence.sufficient() ? SpeakingCriterionStatus.UNAVAILABLE : SpeakingCriterionStatus.INSUFFICIENT_DATA;
        return SpeakingLanguageRubric.missing(status, evidence.sufficient() ? reason : "INSUFFICIENT_LANGUAGE_EVIDENCE",
                evidence.turnCount(), evidence.wordCount());
    }

    private void publish(SpeakingSession session, List<SpeakingAudioAssessment> rows, boolean timedOut) {
        var scores = SpeakingAcousticPolicy.aggregate(rows, timedOut);
        var rubric = session.getLanguageAssessment();
        var languageFeedback = rubric.getFeedback();
        var feedback = new SpeakingAssessmentFeedbackVo(languageFeedback.getStrengthsVi(), languageFeedback.getImprovementsVi(),
                languageFeedback.getRecommendationsVi(), scores.words());
        var outcome = new SpeakingAssessmentVo(scores.pronunciation(), scores.fluency(), rubric.getGrammar(),
                rubric.getVocabulary(), feedback, SpeakingAcousticPolicy.VERSION, SpeakingLanguageRubric.VERSION, clock.instant());
        long count = List.of(outcome.getPronunciation(), outcome.getFluency(), outcome.getGrammar(), outcome.getVocabulary())
                .stream().filter(criterion -> criterion.getScore() != null).count();
        var status = count == 4 ? SpeakingAssessmentStatus.READY : count == 0 ? SpeakingAssessmentStatus.UNAVAILABLE
                : SpeakingAssessmentStatus.PARTIAL;
        repository.publish(session.getId(), status, outcome);
    }

    private SpeakingSession liveSession(String id) {
        try {
            var session = runtime.runtimeSession(id);
            return session.getAssessment() == null ? session : null;
        } catch (SpeakingException missing) {
            if ("SESSION_NOT_FOUND".equals(missing.code())) return null;
            throw missing;
        }
    }

    private boolean audioClosed(SpeakingSession session, Instant now) {
        return !now.isBefore(audioDeadline(session));
    }

    private Instant audioDeadline(SpeakingSession session) {
        if (session.getAssessmentAudioDeadline() != null) return session.getAssessmentAudioDeadline();
        return session.getEndedAt() == null ? Instant.MAX : session.getEndedAt().plus(properties.getAssessment().getAudioDeadline());
    }

    private Duration remaining(Instant deadline) {
        var remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isNegative() || remaining.isZero()) throw new Failure("AUDIO_PROVIDER_TIMEOUT", true);
        return remaining;
    }

    private int attemptLimit() {
        return 1 + properties.getAssessment().getAudioTransientRetries();
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
