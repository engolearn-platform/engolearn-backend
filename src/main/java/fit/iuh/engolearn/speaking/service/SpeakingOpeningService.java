package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.speaking.model.SpeakingAudioAsset;
import fit.iuh.engolearn.speaking.model.SpeakingOpening;
import fit.iuh.engolearn.speaking.utils.port.SpeakingOpeningGenerator;
import fit.iuh.engolearn.speaking.utils.port.SpeechSynthesisPort;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Service
public class SpeakingOpeningService {
    private static final Logger log = LoggerFactory.getLogger(SpeakingOpeningService.class);
    private final SpeakingSessionService sessions;
    private final SpeakingOpeningGenerator generator;
    private final SpeechSynthesisPort synthesis;
    private final MediaStorage media;
    private final ExecutorService workers;
    private final Duration generationTimeout;
    private final Duration audioTimeout;

    @Autowired
    public SpeakingOpeningService(SpeakingSessionService sessions, SpeakingOpeningGenerator generator,
            SpeechSynthesisPort synthesis, MediaStorage media) {
        this(sessions, generator, synthesis, media,
                new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16)),
                Duration.ofSeconds(10), Duration.ofSeconds(20));
    }

    SpeakingOpeningService(SpeakingSessionService sessions, SpeakingOpeningGenerator generator,
            SpeechSynthesisPort synthesis, MediaStorage media, ExecutorService workers,
            Duration generationTimeout, Duration audioTimeout) {
        this.sessions = sessions;
        this.generator = generator;
        this.synthesis = synthesis;
        this.media = media;
        this.workers = workers;
        this.generationTimeout = generationTimeout;
        this.audioTimeout = audioTimeout;
    }

    @EventListener
    public void onOpeningRequested(SpeakingSessionService.OpeningRequested request) {
        runStage(() -> sessions.openingIsCurrent(request, SpeakingOpening.Status.GENERATING)
                ? generator.generate(request.content()) : null, generationTimeout, (draft, error) -> {
            if (error != null || draft == null || draft.reply() == null || draft.reply().isBlank()
                    || draft.hintExampleEn() == null || draft.hintExampleEn().isBlank()) {
                sessions.failOpening(request);
            } else if (sessions.acceptOpeningDraft(request, draft)) {
                runStage(() -> storeAudio(request, draft.reply()), audioTimeout,
                        (asset, audioError) -> sessions.settleOpeningAudio(request, asset));
            }
        });
    }

    private SpeakingAudioAsset storeAudio(SpeakingSessionService.OpeningRequested request, String reply) {
        if (!sessions.openingIsCurrent(request, SpeakingOpening.Status.SYNTHESIZING)) return null;
        byte[] bytes = synthesis.synthesize(reply);
        if (bytes.length == 0 || Thread.currentThread().isInterrupted()
                || !sessions.openingIsCurrent(request, SpeakingOpening.Status.SYNTHESIZING)) return null;
        MediaStorage.StoredAudio stored = media.uploadPrivateAudio(
                "speaking/" + request.sessionId() + "/opening/ai_opening", "mp3", bytes);
        Instant now = Instant.now();
        return SpeakingAudioAsset.builder().id(request.sessionId() + ":opening").sessionId(request.sessionId())
                .kind(SpeakingAudioAsset.Kind.AI_OPENING).cloudinaryPublicId(stored.publicId())
                .format(stored.format()).contentType("audio/mpeg").recordedAt(now)
                .playbackUntil(now.plus(Duration.ofDays(30))).build();
    }

    private <T> void runStage(Supplier<T> call, Duration timeout, BiConsumer<T, Throwable> completed) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Future<?> task = workers.submit(() -> {
                if (result.isDone()) return;
                try {
                    result.complete(call.get());
                } catch (RuntimeException ex) {
                    result.completeExceptionally(ex);
                }
            });
            result.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).whenCompleteAsync((value, error) -> {
                if (error instanceof TimeoutException) task.cancel(true);
                completeStage(completed, value, error, 2);
            });
        } catch (RejectedExecutionException ex) {
            CompletableFuture.runAsync(() -> completeStage(completed, null, ex, 2));
        }
    }

    private <T> void completeStage(BiConsumer<T, Throwable> completed, T value, Throwable error,
            int retries) {
        try {
            completed.accept(value, error);
        } catch (TransientDataAccessException | DataAccessResourceFailureException ex) {
            if (retries > 0) {
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                        .execute(() -> completeStage(completed, value, error, retries - 1));
            } else {
                log.warn("Speaking opening transition failed after retrying persistence");
            }
        } catch (RuntimeException ex) {
            log.warn("Speaking opening transition failed ({})", ex.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void stop() {
        workers.shutdownNow();
    }
}
