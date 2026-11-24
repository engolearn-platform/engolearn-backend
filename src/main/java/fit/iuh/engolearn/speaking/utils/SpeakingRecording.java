package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.utils.port.SpeechStreamPort;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SpeakingRecording {
    private static final int PROVIDER_FRAME = 6400;
    private static final int MAX_PREROLL_BYTES = 32_000;
    private static final int PCM_WINDOW = 320;

    private final SpeechStreamPort stream;
    private final String turnId;
    private final Object forwarding = new Object();
    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
    private final CompletableFuture<Void> ready;
    private volatile boolean receiving = true;
    private volatile boolean failed;
    private boolean started;
    private long nextIndex;
    private long highestObservedIndex = -1;
    private boolean malformedCapture;

    public SpeakingRecording(SpeechStreamPort stream, String turnId, Consumer<String> interim) {
        this.stream = stream;
        this.turnId = turnId;
        CompletableFuture<Void> open;
        try {
            open = stream.open(turnId, new SpeechStreamPort.Listener() {
                @Override public void onInterim(String text) {
                    if (receiving && !failed) interim.accept(text);
                }
                @Override public void onFailure(String message) { failed = true; }
            });
        } catch (RuntimeException error) {
            open = CompletableFuture.failedFuture(error);
        }
        ready = open.thenRun(() -> {
            synchronized (forwarding) {
                if (failed) return;
                stream.beginTurn(turnId);
                forward(audio.toByteArray());
                started = true;
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) failed = true;
        });
    }

    public void add(SpeakingFrames.Learner frame, long limitMs, Runnable markFirstFrame) {
        synchronized (forwarding) {
            if (!receiving || !frame.turnId().toString().equals(turnId)) {
                throw new IllegalArgumentException("Learner audio is out of sequence");
            }
            highestObservedIndex = Math.max(highestObservedIndex, frame.index());
            if (frame.index() != nextIndex) throw new IllegalArgumentException("Learner audio is out of sequence");
            if (audio.size() + frame.payload().length > limitMs * SpeakingWav.PCM_BYTES_PER_MS) {
                throw new IllegalStateException("Learner audio exceeds the turn limit");
            }
            if (nextIndex == 0) markFirstFrame.run();
            audio.writeBytes(frame.payload());
            nextIndex++;
            if (failed) return;
            if (!started) {
                if (audio.size() > MAX_PREROLL_BYTES) failed = true;
                return;
            }
            try {
                forward(frame.payload());
            } catch (RuntimeException error) {
                failed = true;
            }
        }
    }

    private void forward(byte[] pcm) {
        for (int offset = 0; offset < pcm.length; offset += PROVIDER_FRAME) {
            stream.send(turnId, offset == 0 && pcm.length <= PROVIDER_FRAME ? pcm
                    : Arrays.copyOfRange(pcm, offset, Math.min(offset + PROVIDER_FRAME, pcm.length)));
        }
    }

    public byte[] pcm() {
        synchronized (forwarding) {
            return audio.toByteArray();
        }
    }

    public void observeRateLimitedFrame(SpeakingFrames.Learner frame) {
        synchronized (forwarding) {
            if (receiving && turnId.equals(frame.turnId().toString())) {
                highestObservedIndex = Math.max(highestObservedIndex, frame.index());
            }
        }
    }

    public void rejectMalformedFrame() {
        synchronized (forwarding) {
            if (receiving) malformedCapture = true;
        }
    }

    public boolean captureComplete() {
        synchronized (forwarding) {
            return !malformedCapture && nextIndex > highestObservedIndex;
        }
    }

    public CompletableFuture<TimedTranscript> finish() {
        receiving = false;
        long start = System.nanoTime();
        return ready.thenCompose(ignored -> failed
                ? CompletableFuture.failedFuture(new IllegalStateException("Speech recognition unavailable"))
                : stream.endTurn(turnId)).orTimeout(20, TimeUnit.SECONDS)
                .thenApply(text -> new TimedTranscript(text, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
    }

    public void close() {
        receiving = false;
        failed = true;
        stream.close(turnId);
    }

    public String turnId() { return turnId; }

    public static long speechDurationMs(byte[] pcm) {
        long voiced = 0;
        for (int start = 0; start < pcm.length; start += PCM_WINDOW) {
            int end = Math.min(start + PCM_WINDOW, pcm.length);
            long energy = 0;
            for (int i = start; i < end; i += 2) {
                short sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
                energy += (long) sample * sample;
            }
            if (energy >= (end - start) / 2 * 250L * 250L) voiced += end - start;
        }
        return (voiced + SpeakingWav.PCM_BYTES_PER_MS - 1) / SpeakingWav.PCM_BYTES_PER_MS;
    }

    public record TimedTranscript(String original, long waitMs) {}
}
