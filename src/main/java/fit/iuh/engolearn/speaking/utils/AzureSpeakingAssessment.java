package fit.iuh.engolearn.speaking.utils;

import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PullAudioInputStreamCallback;
import fit.iuh.engolearn.speaking.model.vo.SpeakingWordAssessmentVo;
import fit.iuh.engolearn.speaking.utils.port.AcousticAssessmentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class AzureSpeakingAssessment implements AcousticAssessmentPort {
    public static final String LOCALE = "en-US";
    private static final long TICKS_PER_MS = 10_000L;
    private static final int MAX_SEGMENTS = 50;
    private final SpeakingProviderProperties properties;
    private final ObjectMapper mapper;

    @Override
    public Result assess(byte[] pcm, Duration timeout) {
        var configuration = properties.getAzure();
        if (!configuration.isEnabled()) throw new Failure("AUDIO_PROVIDER_DISABLED", false);
        if (blank(configuration.getApiKey()) || blank(configuration.getRegion())) {
            throw new Failure("AUDIO_PROVIDER_NOT_CONFIGURED", false);
        }
        validatePcm(pcm);
        try {
            return recognize(configuration, pcm, timeout);
        } catch (LinkageError failure) {
            throw new Failure("AUDIO_PROVIDER_UNAVAILABLE", false);
        } catch (TimeoutException failure) {
            throw new Failure("AUDIO_PROVIDER_TIMEOUT", true);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new Failure("AUDIO_PROVIDER_TIMEOUT", false);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Failure recognized) throw recognized;
            throw new Failure("AUDIO_PROVIDER_UNAVAILABLE", true);
        } catch (Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new Failure("AUDIO_PROVIDER_UNAVAILABLE", false);
        }
    }

    static void validatePcm(byte[] pcm) {
        if (pcm == null || pcm.length == 0 || pcm.length % Short.BYTES != 0) {
            throw new Failure("AUDIO_CORRUPT", false);
        }
        if (pcm.length > SpeakingAcousticPolicy.MAX_AUDIO_BYTES) {
            throw new Failure("AUDIO_LENGTH_UNSUPPORTED", false);
        }
    }

    private Result recognize(SpeakingProviderProperties.Azure configuration, byte[] pcm, Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        var format = AudioStreamFormat.getWaveFormatPCM(16_000, (short) 16, (short) 1);
        var pcmInput = new PcmInput(pcm);
        try (var speech = SpeechConfig.fromSubscription(configuration.getApiKey(), configuration.getRegion());
             var stream = AudioInputStream.createPullStream(pcmInput, format);
             var input = AudioConfig.fromStreamInput(stream);
             var recognizer = new SpeechRecognizer(configure(speech), input);
             var rubric = new PronunciationAssessmentConfig("", PronunciationAssessmentGradingSystem.HundredMark,
                     PronunciationAssessmentGranularity.Word, false)) {
            rubric.applyTo(recognizer);
            var ended = new CompletableFuture<Void>();
            List<Segment> segments = Collections.synchronizedList(new ArrayList<>());
            long audioTicks = pcm.length * TICKS_PER_MS / SpeakingWav.PCM_BYTES_PER_MS;
            recognizer.recognized.addEventListener((sender, event) -> {
                try (var result = event.getResult()) {
                    if (result.getReason() != ResultReason.RecognizedSpeech) return;
                    addSegment(segments, result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult), audioTicks);
                } catch (RuntimeException invalid) {
                    ended.completeExceptionally(new Failure("INVALID_PROVIDER_RESULT", false));
                }
            });
            recognizer.canceled.addEventListener((sender, event) -> {
                if (event.getReason() == CancellationReason.Error) {
                    ended.completeExceptionally(cancellation(event.getErrorCode()));
                }
            });
            recognizer.sessionStopped.addEventListener((sender, event) -> ended.complete(null));
            await(recognizer.startContinuousRecognitionAsync(), deadline);
            await(ended, deadline);
            return finishRecognition(List.copyOf(segments), pcmInput.complete());
        } finally {
            format.close();
        }
    }

    void addSegment(List<Segment> segments, String response, long audioTicks) {
        synchronized (segments) {
            if (segments.size() >= MAX_SEGMENTS) throw new Failure("INVALID_PROVIDER_RESULT", false);
            segments.add(parse(mapper, response, audioTicks));
        }
    }

    static Result finishRecognition(List<Segment> segments, boolean inputComplete) {
        if (!inputComplete) throw new Failure("AUDIO_PROVIDER_INCOMPLETE", false);
        return combine(segments);
    }

    private static SpeechConfig configure(SpeechConfig speech) {
        speech.setSpeechRecognitionLanguage(LOCALE);
        speech.setOutputFormat(OutputFormat.Detailed);
        speech.requestWordLevelTimestamps();
        // Read to end of stream. A single-shot recognizer can stop at an internal pause.
        speech.setProperty(PropertyId.Speech_SegmentationSilenceTimeoutMs, "5000");
        return speech;
    }

    static Failure cancellation(CancellationErrorCode code) {
        return switch (code) {
            case TooManyRequests -> new Failure("AUDIO_PROVIDER_QUOTA", false);
            case AuthenticationFailure, Forbidden -> new Failure("AUDIO_PROVIDER_AUTH", false);
            case ServiceTimeout -> new Failure("AUDIO_PROVIDER_TIMEOUT", true);
            case ConnectionFailure, ServiceError, ServiceUnavailable -> new Failure("AUDIO_PROVIDER_UNAVAILABLE", true);
            default -> new Failure("AUDIO_PROVIDER_UNAVAILABLE", false);
        };
    }

    static Result combine(List<Segment> segments) {
        if (segments.isEmpty()) return new Result(null, null, 0, List.of(), 0, 0);
        var durations = measure(segments);
        long assessedMs = durations.assessedTicks() / TICKS_PER_MS;
        long fluencyMs = durations.fluencyTicks() / TICKS_PER_MS;
        if (segments.size() == 1) {
            var segment = segments.getFirst();
            return new Result(segment.pronunciation(), segment.fluency(), segment.words(), segment.feedback(), assessedMs, fluencyMs);
        }
        int words = segments.stream().mapToInt(Segment::words).sum();
        boolean valid = words > 0 && segments.stream().allMatch(segment -> segment.words() > 0
                && SpeakingAcousticPolicy.validScore(segment.pronunciation()));
        Double pronunciation = valid ? segments.stream().mapToDouble(segment ->
                segment.pronunciation() * segment.words()).sum() / words : null;
        Double fluency = durations.fluencyTicks() > 0 ? durations.weightedFluency() / durations.fluencyTicks() : null;
        String fluencyReason = fluency != null ? null
                : segments.stream().anyMatch(segment -> SpeakingAcousticPolicy.validScore(segment.fluency()))
                ? "INSUFFICIENT_RECOGNIZED_SPEECH" : "INVALID_PROVIDER_RESULT";
        var feedback = segments.stream().flatMap(segment -> segment.feedback().stream()).limit(500).toList();
        return new Result(pronunciation, fluency, words, feedback, valid ? null : "INVALID_PROVIDER_RESULT", fluencyReason,
                assessedMs, fluencyMs);
    }

    private static Durations measure(List<Segment> segments) {
        var intervals = segments.stream().flatMap(segment -> segment.timings().stream()
                        .map(timing -> new TimedFluency(timing, segment.fluency())))
                .sorted(Comparator.comparingLong(evidence -> evidence.timing().start()))
                .toList();
        long assessedEnd = 0;
        long fluencyEnd = 0;
        long assessedTicks = 0;
        long fluencyTicks = 0;
        double weightedFluency = 0;
        // Earlier interval starts own overlaps. Stable sorting breaks ties by Azure segment order.
        for (var evidence : intervals) {
            var timing = evidence.timing();
            assessedTicks += Math.max(0, timing.end() - Math.max(assessedEnd, timing.start()));
            assessedEnd = Math.max(assessedEnd, timing.end());
            if (SpeakingAcousticPolicy.validScore(evidence.fluency())) {
                long weight = Math.max(0, timing.end() - Math.max(fluencyEnd, timing.start()));
                fluencyEnd = Math.max(fluencyEnd, timing.end());
                fluencyTicks += weight;
                weightedFluency += evidence.fluency() * weight;
            }
        }
        return new Durations(assessedTicks, fluencyTicks, weightedFluency);
    }

    private static <T> T await(Future<T> pending, long deadline) throws InterruptedException, ExecutionException, TimeoutException {
        return pending.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    static Segment parse(ObjectMapper mapper, String response, long audioTicks) {
        if (response == null || response.isBlank() || response.length() > 512_000) {
            throw new Failure("INVALID_PROVIDER_RESULT", false);
        }
        JsonNode phrase = mapper.readTree(response);
        JsonNode best = phrase.path("NBest").path(0);
        if (!best.isObject()) throw new Failure("INVALID_PROVIDER_RESULT", false);
        JsonNode assessed = best.path("PronunciationAssessment");
        var words = new ArrayList<SpeakingWordAssessmentVo>();
        var timings = new ArrayList<Interval>();
        int recognized = 0;
        for (JsonNode node : best.path("Words")) {
            JsonNode score = node.path("PronunciationAssessment");
            String word = text(node.path("Word"));
            String error = text(score.path("ErrorType"));
            if (word == null || word.isBlank() || word.length() > 64 || "Omission".equals(error)
                    || word.codePoints().noneMatch(Character::isLetter)) continue;
            recognized++;
            var timing = interval(node, audioTicks);
            if (timing != null) timings.add(timing);
            var evidence = new SpeakingWordAssessmentVo(word, score(score.path("AccuracyScore")), error);
            if (SpeakingAcousticPolicy.usableWord(evidence)) words.add(evidence);
        }
        if (timings.isEmpty() && recognized > 0) {
            var timing = interval(phrase, audioTicks);
            if (timing != null) timings.add(timing);
        }
        return new Segment(score(assessed.path("AccuracyScore")), score(assessed.path("FluencyScore")), recognized,
                List.copyOf(words), List.copyOf(timings));
    }

    private static Interval interval(JsonNode node, long audioTicks) {
        JsonNode offset = node.path("Offset");
        JsonNode duration = node.path("Duration");
        if (!offset.isIntegralNumber() || !offset.canConvertToLong()
                || !duration.isIntegralNumber() || !duration.canConvertToLong()) return null;
        long start = offset.asLong();
        long length = duration.asLong();
        return start >= 0 && start < audioTicks && length > 0 && length <= audioTicks - start
                ? new Interval(start, start + length) : null;
    }

    record Segment(Double pronunciation, Double fluency, int words, List<SpeakingWordAssessmentVo> feedback,
                   List<Interval> timings) {}
    private record Interval(long start, long end) {}
    private record TimedFluency(Interval timing, Double fluency) {}
    private record Durations(long assessedTicks, long fluencyTicks, double weightedFluency) {}

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }

    private static Double score(JsonNode value) {
        Double result = value.isNumber() ? value.asDouble() : null;
        return SpeakingAcousticPolicy.validScore(result) ? result : null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class PcmInput extends PullAudioInputStreamCallback {
        private final byte[] pcm;
        private volatile int position;

        private PcmInput(byte[] pcm) {
            this.pcm = pcm;
        }

        @Override
        public int read(byte[] target) {
            int count = Math.min(target.length, pcm.length - position);
            System.arraycopy(pcm, position, target, 0, count);
            position += count;
            return count;
        }

        boolean complete() {
            return position == pcm.length;
        }

        @Override
        public void close() {}
    }
}
