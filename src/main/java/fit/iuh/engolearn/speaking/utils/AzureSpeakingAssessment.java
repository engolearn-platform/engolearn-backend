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
import java.util.List;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class AzureSpeakingAssessment implements AcousticAssessmentPort {
    public static final String LOCALE = "en-US";
    private final SpeakingProviderProperties properties;
    private final ObjectMapper mapper;

    @Override
    public Result assess(byte[] pcm, Duration timeout) {
        var configuration = properties.getAzure();
        if (!configuration.isEnabled()) throw new Failure("AUDIO_PROVIDER_DISABLED", false);
        if (blank(configuration.getApiKey()) || blank(configuration.getRegion())) {
            throw new Failure("AUDIO_PROVIDER_NOT_CONFIGURED", false);
        }
        if (pcm.length > SpeakingAcousticPolicy.MAX_SINGLE_AUDIO_MS * SpeakingWav.PCM_BYTES_PER_MS) {
            throw new Failure("AUDIO_LENGTH_UNSUPPORTED", false);
        }
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
            List<Result> segments = Collections.synchronizedList(new ArrayList<>());
            recognizer.recognized.addEventListener((sender, event) -> {
                try (var result = event.getResult()) {
                    if (result.getReason() != ResultReason.RecognizedSpeech) return;
                    if (segments.size() >= 50) throw new Failure("INVALID_PROVIDER_RESULT", false);
                    segments.add(parse(mapper, result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult)));
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
            if (!pcmInput.complete()) throw new Failure("AUDIO_PROVIDER_INCOMPLETE", false);
            return combine(List.copyOf(segments));
        } finally {
            format.close();
        }
    }

    private static SpeechConfig configure(SpeechConfig speech) {
        speech.setSpeechRecognitionLanguage(LOCALE);
        speech.setOutputFormat(OutputFormat.Detailed);
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

    static Result combine(List<Result> segments) {
        if (segments.isEmpty()) return new Result(null, null, 0, List.of());
        if (segments.size() == 1) return segments.getFirst();
        int words = segments.stream().mapToInt(Result::words).sum();
        boolean valid = words > 0 && segments.stream().allMatch(segment -> segment.words() > 0
                && SpeakingAcousticPolicy.validScore(segment.pronunciation()));
        Double pronunciation = valid ? segments.stream().mapToDouble(segment ->
                segment.pronunciation() * segment.words()).sum() / words : null;
        // Phrase-level fluency excludes pauses between Azure segments. There is no native whole-turn score here.
        var feedback = segments.stream().flatMap(segment -> segment.feedback().stream()).limit(500).toList();
        return new Result(pronunciation, null, words, feedback, valid ? null : "INVALID_PROVIDER_RESULT",
                "AUDIO_SEGMENTED_FLUENCY_UNAVAILABLE");
    }

    private static <T> T await(Future<T> pending, long deadline) throws InterruptedException, ExecutionException, TimeoutException {
        return pending.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    static Result parse(ObjectMapper mapper, String response) {
        if (response == null || response.isBlank() || response.length() > 512_000) {
            throw new Failure("INVALID_PROVIDER_RESULT", false);
        }
        JsonNode best = mapper.readTree(response).path("NBest").path(0);
        if (!best.isObject()) throw new Failure("INVALID_PROVIDER_RESULT", false);
        JsonNode assessed = best.path("PronunciationAssessment");
        var words = new ArrayList<SpeakingWordAssessmentVo>();
        int recognized = 0;
        for (JsonNode node : best.path("Words")) {
            JsonNode score = node.path("PronunciationAssessment");
            String word = text(node.path("Word"));
            String error = text(score.path("ErrorType"));
            if (word == null || word.isBlank() || word.length() > 64 || "Omission".equals(error)
                    || word.codePoints().noneMatch(Character::isLetter)) continue;
            recognized++;
            var evidence = new SpeakingWordAssessmentVo(word, score(score.path("AccuracyScore")), error);
            if (SpeakingAcousticPolicy.usableWord(evidence)) words.add(evidence);
        }
        return new Result(score(assessed.path("AccuracyScore")), score(assessed.path("FluencyScore")), recognized, List.copyOf(words));
    }

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
