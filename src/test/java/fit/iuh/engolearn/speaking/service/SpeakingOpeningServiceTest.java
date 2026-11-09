package fit.iuh.engolearn.speaking.service;

import fit.iuh.engolearn.shared.media.MediaStorage;
import fit.iuh.engolearn.speaking.model.SpeakingAudioAsset;
import fit.iuh.engolearn.speaking.model.SpeakingOpening;
import fit.iuh.engolearn.speaking.utils.port.SpeakingOpeningGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakingOpeningServiceTest {
    private static final SpeakingSessionService.OpeningRequested REQUEST =
            new SpeakingSessionService.OpeningRequested("session", "attempt", null);
    private static final SpeakingOpeningGenerator.Draft DRAFT =
            new SpeakingOpeningGenerator.Draft("What would you like?", "I'd like an iced coffee.");

    @Test
    void synthesizesTheCommittedReplyAndSavesSessionAudio() throws InterruptedException {
        Sessions sessions = new Sessions();
        AtomicInteger uploads = new AtomicInteger();
        try (var workers = Executors.newSingleThreadExecutor()) {
            var service = new SpeakingOpeningService(sessions, content -> DRAFT, reply -> {
                assertThat(reply).isEqualTo(DRAFT.reply());
                return new byte[] {1};
            }, media(uploads), workers, Duration.ofSeconds(1), Duration.ofSeconds(1));
            service.onOpeningRequested(REQUEST);
            assertThat(sessions.settled.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(sessions.draft).isEqualTo(DRAFT);
        assertThat(sessions.asset.getKind()).isEqualTo(SpeakingAudioAsset.Kind.AI_OPENING);
        assertThat(sessions.asset.getSessionId()).isEqualTo("session");
        assertThat(sessions.asset.getTurnId()).isNull();
        assertThat(uploads.get()).isOne();
    }

    @Test
    void generationFailureDoesNotSynthesize() throws InterruptedException {
        Sessions sessions = new Sessions();
        AtomicInteger synthesis = new AtomicInteger();
        try (var workers = Executors.newSingleThreadExecutor()) {
            var service = new SpeakingOpeningService(sessions, content -> {
                throw new IllegalStateException("provider unavailable");
            }, reply -> {
                synthesis.incrementAndGet();
                return new byte[] {1};
            }, media(new AtomicInteger()), workers, Duration.ofSeconds(1), Duration.ofSeconds(1));
            service.onOpeningRequested(REQUEST);
            assertThat(sessions.settled.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(sessions.generationFailed).isTrue();
        assertThat(synthesis.get()).isZero();
    }

    @Test
    void retriesPersistenceWithoutCallingTheGeneratorAgain() throws InterruptedException {
        Sessions sessions = new Sessions();
        sessions.failedAccepts = 1;
        AtomicInteger generations = new AtomicInteger();
        try (var workers = Executors.newSingleThreadExecutor()) {
            var service = new SpeakingOpeningService(sessions, content -> {
                generations.incrementAndGet();
                return DRAFT;
            }, reply -> new byte[] {1}, media(new AtomicInteger()), workers,
                    Duration.ofSeconds(1), Duration.ofSeconds(1));
            service.onOpeningRequested(REQUEST);
            assertThat(sessions.settled.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(generations.get()).isOne();
        assertThat(sessions.asset).isNotNull();
    }

    @Test
    void audioTimeoutSettlesWithTextAndHint() throws InterruptedException {
        Sessions sessions = new Sessions();
        try (var workers = Executors.newSingleThreadExecutor()) {
            var service = new SpeakingOpeningService(sessions, content -> DRAFT, reply -> {
                try {
                    new CountDownLatch(1).await();
                    throw new IllegalStateException("unexpected release");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }, media(new AtomicInteger()), workers, Duration.ofSeconds(1), Duration.ofMillis(100));
            service.onOpeningRequested(REQUEST);
            assertThat(sessions.settled.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(sessions.draft).isEqualTo(DRAFT);
        assertThat(sessions.asset).isNull();
        assertThat(sessions.generationFailed).isFalse();
    }

    private static MediaStorage media(AtomicInteger uploads) {
        return new MediaStorage() {
            @Override
            public StoredAudio uploadPrivateAudio(String publicId, String format, byte[] bytes) {
                uploads.incrementAndGet();
                return new StoredAudio(publicId, format);
            }

            @Override
            public URI privateAudioUrl(String publicId, String format, Duration lifetime) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class Sessions extends SpeakingSessionService {
        private final CountDownLatch settled = new CountDownLatch(1);
        private SpeakingOpeningGenerator.Draft draft;
        private SpeakingAudioAsset asset;
        private boolean generationFailed;
        private int failedAccepts;

        private Sessions() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        boolean openingIsCurrent(OpeningRequested request, SpeakingOpening.Status status) {
            return true;
        }

        @Override
        boolean acceptOpeningDraft(OpeningRequested request, SpeakingOpeningGenerator.Draft draft) {
            if (failedAccepts-- > 0) throw new DataAccessResourceFailureException("offline");
            this.draft = draft;
            return true;
        }

        @Override
        void failOpening(OpeningRequested request) {
            generationFailed = true;
            settled.countDown();
        }

        @Override
        void settleOpeningAudio(OpeningRequested request, SpeakingAudioAsset asset) {
            this.asset = asset;
            settled.countDown();
        }
    }
}
