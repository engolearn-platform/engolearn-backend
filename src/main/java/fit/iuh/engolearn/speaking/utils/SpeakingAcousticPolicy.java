package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.SpeakingAudioAssessment;
import fit.iuh.engolearn.speaking.model.enums.SpeakingAudioAssessmentStatus;
import fit.iuh.engolearn.speaking.model.enums.SpeakingCriterionStatus;
import fit.iuh.engolearn.speaking.model.vo.SpeakingCriterionVo;
import fit.iuh.engolearn.speaking.model.vo.SpeakingWordAssessmentVo;
import fit.iuh.engolearn.speaking.utils.port.AcousticAssessmentPort;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

import static fit.iuh.engolearn.speaking.model.enums.SpeakingCriterionStatus.*;

public final class SpeakingAcousticPolicy {
    public static final String VERSION = "azure-v2.2";
    static final int MAX_AUDIO_BYTES = SpeakingSessionSettings.MAX_TURN_AUDIO_MS * SpeakingWav.PCM_BYTES_PER_MS;
    private static final int MIN_FLUENCY_MS = 4000;
    private static final String INSUFFICIENT_SPEECH = "INSUFFICIENT_RECOGNIZED_SPEECH";
    private static final int WINDOW = 320;
    private static final int BOUNDARY_BYTES = 150 * SpeakingWav.PCM_BYTES_PER_MS;
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'’-]{0,63}");

    private SpeakingAcousticPolicy() {}

    public static Prepared prepare(byte[] pcm, long limitMs) {
        if (pcm == null || pcm.length % 2 != 0 || pcm.length / (double) SpeakingWav.PCM_BYTES_PER_MS > limitMs) {
            return rejected("AUDIO_CORRUPT");
        }
        if (pcm.length > MAX_AUDIO_BYTES) return rejected("AUDIO_LENGTH_UNSUPPORTED");
        int firstVoice = -1;
        int lastVoice = 0;
        int voicedBytes = 0;
        int clipped = 0;
        for (int start = 0; start < pcm.length; start += WINDOW) {
            int end = Math.min(pcm.length, start + WINDOW);
            long energy = 0;
            int crossings = 0;
            int previous = 0;
            int active = 0;
            for (int i = start; i < end; i += 2) {
                int value = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
                energy += (long) value * value;
                if (Math.abs(value) >= 32760) clipped++;
                if (Math.abs(value) >= 80) {
                    if (previous != 0 && (previous < 0) != (value < 0)) crossings++;
                    previous = value;
                    active++;
                }
            }
            if (energy >= (end - start) / 2 * 250L * 250L && crossings >= 1 && crossings * 4 < active) {
                if (firstVoice < 0) firstVoice = start;
                lastVoice = end;
                voicedBytes += end - start;
            }
        }
        if (clipped * 20L > pcm.length / 2) return rejected("AUDIO_CORRUPT");
        if (firstVoice < 0) return rejected("NO_SPEECH");
        int start = Math.max(0, firstVoice - BOUNDARY_BYTES);
        int end = Math.min(pcm.length, lastVoice + BOUNDARY_BYTES);
        long voicedMs = voicedBytes / SpeakingWav.PCM_BYTES_PER_MS;
        if (voicedMs < 2000) return rejected("INSUFFICIENT_AUDIO");
        return new Prepared(Arrays.copyOfRange(pcm, start, end), (end - start) / SpeakingWav.PCM_BYTES_PER_MS, voicedMs, null);
    }

    private static Prepared rejected(String reason) {
        return new Prepared(null, 0, 0, reason);
    }

    public static Sample sample(long voicedMs, AcousticAssessmentPort.Result result) {
        if (result == null) {
            return new Sample(null, null, 0, List.of(), "INVALID_PROVIDER_RESULT", "INVALID_PROVIDER_RESULT", 0, 0);
        }
        int words = Math.max(0, result.words());
        String pronunciationReason = scoreReason(voicedMs, words, 2000, 3, result.pronunciation(), result.pronunciationReason());
        String fluencyReason = scoreReason(voicedMs, words, MIN_FLUENCY_MS, 5, result.fluency(), result.fluencyReason());
        if (fluencyReason == null && result.fluencyEvidenceDurationMs() < MIN_FLUENCY_MS) {
            fluencyReason = INSUFFICIENT_SPEECH;
        }
        return new Sample(pronunciationReason == null ? result.pronunciation() : null,
                fluencyReason == null ? result.fluency() : null, words,
                result.feedback() == null ? List.of() : result.feedback().stream()
                        .filter(SpeakingAcousticPolicy::usableWord).limit(500).toList(),
                pronunciationReason, fluencyReason, result.assessedSpeechDurationMs(), result.fluencyEvidenceDurationMs());
    }

    static boolean validScore(Double score) {
        return score != null && Double.isFinite(score) && score >= 0 && score <= 100;
    }

    private static String scoreReason(long voicedMs, int words, long minimumMs, int minimumWords, Double score, String reason) {
        if (words < minimumWords || voicedMs < minimumMs) return INSUFFICIENT_SPEECH;
        return validScore(score) ? null : reason == null ? "INVALID_PROVIDER_RESULT" : reason;
    }

    public static Scores aggregate(List<SpeakingAudioAssessment> rows, boolean deadlineReached) {
        var unique = new LinkedHashMap<String, SpeakingAudioAssessment>();
        for (var row : rows) unique.putIfAbsent(row.getId(), row);
        var samples = List.copyOf(unique.values());
        var pronunciation = criterion(samples, SpeakingAudioAssessment::getPronunciation,
                SpeakingAudioAssessment::getPronunciationReason, SpeakingAudioAssessment::getWords,
                row -> knownDuration(row.getAssessedSpeechDurationMs()), 20, 15_000, deadlineReached);
        ToLongFunction<SpeakingAudioAssessment> fluencyDuration = row -> knownDuration(row.getFluencyEvidenceDurationMs());
        var fluency = criterion(samples, SpeakingAudioAssessment::getFluency,
                SpeakingAudioAssessment::getFluencyReason, fluencyDuration, fluencyDuration, 0, 20_000, deadlineReached);
        return new Scores(pronunciation, fluency, practiceWords(samples));
    }

    private static SpeakingCriterionVo criterion(List<SpeakingAudioAssessment> samples,
                                                 Function<SpeakingAudioAssessment, Double> score,
                                                 Function<SpeakingAudioAssessment, String> missing,
                                                 ToLongFunction<SpeakingAudioAssessment> weight,
                                                 ToLongFunction<SpeakingAudioAssessment> speechDuration,
                                                 int minimumWords, int minimumDuration, boolean deadlineReached) {
        var eligible = samples.stream().filter(row -> validScore(score.apply(row)) && weight.applyAsLong(row) > 0).toList();
        int words = eligible.stream().mapToInt(SpeakingAudioAssessment::getWords).sum();
        long duration = eligible.stream().mapToLong(speechDuration).sum();
        long denominator = eligible.stream().mapToLong(weight).sum();
        boolean assessed = eligible.size() >= 3 && words >= minimumWords && duration >= minimumDuration && denominator > 0;
        String technical = samples.stream().filter(row -> row.getStatus() == SpeakingAudioAssessmentStatus.FAILED
                        || row.getStatus() == SpeakingAudioAssessmentStatus.ASSESSED && missing.apply(row) != null
                        && !INSUFFICIENT_SPEECH.equals(missing.apply(row)))
                .map(row -> missing.apply(row) == null ? row.getReason() : missing.apply(row))
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (technical == null && deadlineReached) technical = "AUDIO_DEADLINE_EXCEEDED";
        String unsupported = samples.stream().map(SpeakingAudioAssessment::getReason)
                .filter(reason -> "AUDIO_LENGTH_UNSUPPORTED".equals(reason) || "AUDIO_CORRUPT".equals(reason)
                        || "AUDIO_INCOMPLETE".equals(reason))
                .findFirst().orElse(null);
        SpeakingCriterionStatus status = assessed ? ASSESSED : technical == null ? INSUFFICIENT_DATA : UNAVAILABLE;
        Double value = assessed ? eligible.stream().mapToDouble(row -> score.apply(row) * weight.applyAsLong(row)).sum()
                / denominator : null;
        String reason = assessed ? null : technical != null ? technical
                : unsupported != null ? unsupported : "INSUFFICIENT_AUDIO_EVIDENCE";
        return SpeakingCriterionVo.builder().status(status).score(value).reason(reason)
                .sampleCount(eligible.size()).wordCount(words).speechDurationMs(duration).examples(List.of()).build();
    }

    private static long knownDuration(Long duration) {
        return duration == null ? 0 : duration;
    }

    private static List<SpeakingWordAssessmentVo> practiceWords(List<SpeakingAudioAssessment> samples) {
        var words = new HashMap<String, List<SpeakingWordAssessmentVo>>();
        for (var row : samples) {
            if (row.getWordFeedback() == null || row.getPronunciation() == null) continue;
            for (var word : row.getWordFeedback()) {
                if (usableWord(word) && word.getAccuracyScore() < 60 && "Mispronunciation".equals(word.getErrorType())) {
                    words.computeIfAbsent(word.getWord().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(word);
                }
            }
        }
        return words.entrySet().stream().sorted(Comparator
                        .<Map.Entry<String, List<SpeakingWordAssessmentVo>>>comparingInt(entry -> entry.getValue().size())
                        .reversed().thenComparingDouble(entry -> lowestAccuracy(entry.getValue()))
                        .thenComparing(Map.Entry::getKey)).limit(5)
                .map(entry -> new SpeakingWordAssessmentVo(entry.getKey(), lowestAccuracy(entry.getValue()), "Mispronunciation"))
                .toList();
    }

    static boolean usableWord(SpeakingWordAssessmentVo word) {
        return word != null && word.getWord() != null && WORD.matcher(word.getWord()).matches()
                && validScore(word.getAccuracyScore()) && word.getErrorType() != null && word.getErrorType().length() <= 32;
    }

    private static double lowestAccuracy(List<SpeakingWordAssessmentVo> words) {
        return words.stream().mapToDouble(SpeakingWordAssessmentVo::getAccuracyScore).min().orElseThrow();
    }

    public record Prepared(byte[] pcm, long durationMs, long voicedMs, String reason) {}
    public record Sample(Double pronunciation, Double fluency, int words, List<SpeakingWordAssessmentVo> feedback,
                         String pronunciationReason, String fluencyReason, long assessedSpeechDurationMs,
                         long fluencyEvidenceDurationMs) {}
    public record Scores(SpeakingCriterionVo pronunciation, SpeakingCriterionVo fluency,
                         List<SpeakingWordAssessmentVo> words) {}
}
