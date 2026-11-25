package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.SpeakingAudioAssessment;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import fit.iuh.engolearn.speaking.utils.port.AcousticAssessmentPort;
import fit.iuh.engolearn.speaking.utils.port.LanguageAssessmentPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SpeakingAssessmentPolicyTest {
    @Test
    void missingOrDisabledProvidersFailBeforeOpeningSdkClients() {
        var properties = new SpeakingProviderProperties();
        var azure = new AzureSpeakingAssessment(properties, JsonMapper.builder().build());
        var gemini = new GeminiLanguageAssessment(properties, new GeminiStructuredSpeakingGeneration(properties));
        var timeout = Duration.ofSeconds(1);
        assertThatThrownBy(() -> azure.assess(new byte[0], timeout))
                .isInstanceOf(AcousticAssessmentPort.Failure.class).hasMessage("AUDIO_PROVIDER_NOT_CONFIGURED");
        assertThatThrownBy(() -> gemini.assess("Rubric", "{}", List.of(), SpeakingLanguageRubric.SCHEMA, timeout))
                .isInstanceOf(LanguageAssessmentPort.Failure.class).hasMessage("LANGUAGE_PROVIDER_NOT_CONFIGURED");
        properties.getAzure().setEnabled(false);
        properties.getAssessment().setLanguageEnabled(false);
        assertThatThrownBy(() -> azure.assess(new byte[0], timeout))
                .isInstanceOf(AcousticAssessmentPort.Failure.class).hasMessage("AUDIO_PROVIDER_DISABLED");
        assertThatThrownBy(() -> gemini.assess("Rubric", "{}", List.of(), SpeakingLanguageRubric.SCHEMA, timeout))
                .isInstanceOf(LanguageAssessmentPort.Failure.class).hasMessage("LANGUAGE_PROVIDER_DISABLED");
    }

    @Test
    void trimsOnlyBoundarySilenceAndRejectsMalformedClippedOrUnsupportedAudio() {
        var pcm = phrase(8000, 1000, 3000, 1500);
        var checked = SpeakingAcousticPolicy.prepare(pcm, 120_000);
        assertThat(checked.reason()).isNull();
        assertThat(checked.durationMs()).isEqualTo(5800);
        for (int index = 3200 * 32; index < 4100 * 32; index++) {
            assertThat(checked.pcm()[index]).isZero();
        }
        assertThat(SpeakingAcousticPolicy.prepare(new byte[67], 120_000).reason()).isEqualTo("AUDIO_CORRUPT");
        assertThat(SpeakingAcousticPolicy.prepare(new byte[160_000], 120_000).reason()).isEqualTo("NO_SPEECH");
        var clipped = ByteBuffer.allocate(160_000).order(ByteOrder.LITTLE_ENDIAN);
        while (clipped.hasRemaining()) clipped.putShort(Short.MAX_VALUE);
        assertThat(SpeakingAcousticPolicy.prepare(clipped.array(), 120_000).reason()).isEqualTo("AUDIO_CORRUPT");
        assertThat(SpeakingAcousticPolicy.prepare(new byte[3_840_002], 120_000).reason()).isEqualTo("AUDIO_CORRUPT");
        assertThat(SpeakingAcousticPolicy.prepare(phrase(2500, 0, 1500, 0), 120_000).reason())
                .isEqualTo("INSUFFICIENT_AUDIO");
    }

    @ParameterizedTest
    @ValueSource(ints = {320_000, 960_000, 960_002, 1_920_000, 2_880_000, 3_840_000})
    void acceptsFullAudioThroughTheTurnLimit(int size) {
        byte[] voice = phrase(10_000, 10, 4000, 0);
        byte[] pcm = new byte[size];
        for (int i = 0; i < size; i += 32_000) {
            System.arraycopy(voice, 32_000, pcm, i, Math.min(32_000, size - i));
        }
        var prepared = SpeakingAcousticPolicy.prepare(pcm, 120_000);
        assertThat(prepared.reason()).isNull();
        assertThat(prepared.pcm()).isEqualTo(pcm);
        assertThat(prepared.durationMs()).isEqualTo(size / 32);
        AzureSpeakingAssessment.validatePcm(prepared.pcm());
        byte[] aboveLimit = Arrays.copyOf(pcm, size + 2);
        assertThat(SpeakingAcousticPolicy.prepare(aboveLimit, size / 32).reason()).isEqualTo("AUDIO_CORRUPT");
    }

    @Test
    void rejectsOversizedOrMalformedProviderPcmBeforeOpeningTheSdk() {
        assertThatThrownBy(() -> AzureSpeakingAssessment.validatePcm(new byte[3_840_002]))
                .isInstanceOf(AcousticAssessmentPort.Failure.class).hasMessage("AUDIO_LENGTH_UNSUPPORTED");
        assertThatThrownBy(() -> AzureSpeakingAssessment.validatePcm(new byte[67]))
                .isInstanceOf(AcousticAssessmentPort.Failure.class).hasMessage("AUDIO_CORRUPT");
    }

    @Test
    void legacyPreparedDurationIsNotRecognizedEvidence() {
        var scores = SpeakingAcousticPolicy.aggregate(List.of(
                SpeakingAudioAssessment.builder().id("a").words(10).durationMs(40_000).pronunciation(50d).fluency(50d).build(),
                SpeakingAudioAssessment.builder().id("b").words(10).durationMs(40_000).pronunciation(60d).fluency(60d).build(),
                SpeakingAudioAssessment.builder().id("c").words(10).durationMs(40_000).pronunciation(70d).fluency(70d).build()), false);
        assertThat(scores.pronunciation().getScore()).isNull();
        assertThat(scores.fluency().getScore()).isNull();
    }

    @Test
    void recognizedWordsFromAnUntimedTurnStillContributeToPronunciation() {
        var untimed = SpeakingAudioAssessment.builder().id("c").words(8).durationMs(40_000).pronunciation(90d).fluency(90d).build();
        var scores = SpeakingAcousticPolicy.aggregate(List.of(sample("a", 8, 8000, 30d, 50d),
                sample("b", 8, 8000, 60d, 80d), untimed), false);
        assertThat(scores.pronunciation().getScore()).isEqualTo(60);
        assertThat(scores.pronunciation().getSpeechDurationMs()).isEqualTo(16_000L);
        assertThat(scores.fluency().getSampleCount()).isEqualTo(2);
        assertThat(scores.fluency().getScore()).isNull();
    }

    @Test
    void weightsOnlyConfidenceIndependentEligibleScoresAndPreservesZero() {
        var first = sample("a", 5, 5000, 0d, null);
        var second = sample("b", 7, 6000, 70d, 15d);
        var third = sample("c", 8, 7000, 100d, 30d);
        var fourth = sample("d", 6, 8000, null, 90d);
        var scores = SpeakingAcousticPolicy.aggregate(List.of(first, second, third, fourth, second), false);
        assertThat(scores.pronunciation().getScore()).isEqualTo(64.5);
        assertThat(scores.fluency().getScore()).isEqualTo(1020.0 / 21);
        assertThat(scores.pronunciation().getWordCount()).isEqualTo(20);
        assertThat(scores.pronunciation().getSpeechDurationMs()).isEqualTo(18_000L);
        assertThat(scores.fluency().getSpeechDurationMs()).isEqualTo(21_000L);
        assertThat(SpeakingAcousticPolicy.aggregate(List.of(first), false).pronunciation().getScore()).isNull();
        var zeroSample = SpeakingAcousticPolicy.sample(6000, new AcousticAssessmentPort.Result(0d, 0d, 5, List.of(), 4000, 4000));
        assertThat(zeroSample.pronunciation()).isZero();
        assertThat(zeroSample.fluency()).isZero();
        var shortSample = SpeakingAcousticPolicy.sample(3200, new AcousticAssessmentPort.Result(0d, 0d, 3, List.of(), 3100, 3100));
        assertThat(shortSample.pronunciation()).isZero();
        assertThat(shortSample.fluency()).isNull();
        assertThat(SpeakingAcousticPolicy.sample(9000, new AcousticAssessmentPort.Result(-1d, 101d, 8, List.of(), 8000, 0))
                .pronunciation()).isNull();
    }

    @Test
    void mapsOnlyAzureAccuracyFluencyAndRecognizedWordEvidence() {
        var raw = """
                {"Offset":0,"Duration":120000000,"NBest":[{"PronunciationAssessment":{"AccuracyScore":0,"FluencyScore":92,"PronScore":99,"CompletenessScore":100},
                "Words":[{"Word":"yesterday","Offset":20000000,"Duration":40000000,"PronunciationAssessment":{"AccuracyScore":47,"ErrorType":"Mispronunciation"}},
                {"Word":"ghost","Offset":80000000,"Duration":30000000,"PronunciationAssessment":{"AccuracyScore":200,"ErrorType":"Omission"}},
                {"Word":"hello","PronunciationAssessment":{"AccuracyScore":71,"ErrorType":"None"}}]}]}
                """;
        var mapped = AzureSpeakingAssessment.combine(List.of(AzureSpeakingAssessment.parse(JsonMapper.builder().build(), raw, 120000000)));
        assertThat(mapped.pronunciation()).isZero();
        assertThat(mapped.fluency()).isEqualTo(92);
        assertThat(mapped.words()).isEqualTo(2);
        assertThat(mapped.assessedSpeechDurationMs()).isEqualTo(4_000);
        assertThat(mapped.fluencyEvidenceDurationMs()).isEqualTo(4_000);
        assertThat(mapped.feedback()).extracting(word -> word.getWord()).containsExactly("yesterday", "hello");
    }

    @Test
    void preservesWordWeightedAccuracyWhenFluencyTimingIsUnavailable() {
        var combined = AzureSpeakingAssessment.combine(List.of(
                new AzureSpeakingAssessment.Segment(40d, 20d, 3, List.of(), List.of()),
                new AzureSpeakingAssessment.Segment(100d, 80d, 7, List.of(), List.of())));
        var sample = SpeakingAcousticPolicy.sample(9000, combined);
        assertThat(sample.pronunciation()).isEqualTo(82);
        assertThat(sample.fluency()).isNull();
        assertThat(sample.fluencyReason()).isEqualTo("INSUFFICIENT_RECOGNIZED_SPEECH");
        assertThat(sample.words()).isEqualTo(10);
    }

    @Test
    void weightsFluencyByUnequalRecognizedDurationsWithoutRoundingSegments() {
        var combined = AzureSpeakingAssessment.combine(List.of(
                segment("85", "0", "150000000"),
                segment("70", "200000000", "200000000"),
                segment("90", "450000000", "250000000")));
        assertThat(combined.fluency()).isEqualTo(82.08333333333333);
        assertThat(combined.fluencyEvidenceDurationMs()).isEqualTo(60_000);
    }

    @Test
    void weightsFluencyEquallyForEqualDurationsAndIncludesZero() {
        var combined = AzureSpeakingAssessment.combine(List.of(
                segment("0", "0", "40000000"),
                segment("90", "50000000", "40000000")));
        assertThat(combined.fluency()).isEqualTo(45);
    }

    @Test
    void fluencyExcludesInvalidScoresAndTimingsWithoutConsumingValidOverlap() {
        var combined = AzureSpeakingAssessment.combine(List.of(
                segment("null", "0", "120000000"),
                segment("101", "0", "120000000"),
                segment("1e309", "0", "120000000"),
                segment("80", "70000000", "null"),
                segment("80", "70000000", "0"),
                segment("80", "-10000000", "100000000"),
                segment("80", "1190000000", "20000000"),
                segment("20", "0", "50000000"),
                segment("90", "30000000", "40000000")));
        assertThat(combined.fluency()).isEqualTo(40);
        assertThat(combined.pronunciation()).isEqualTo(75);
        assertThat(combined.assessedSpeechDurationMs()).isEqualTo(12_000);
        assertThat(combined.fluencyEvidenceDurationMs()).isEqualTo(7_000);
    }

    @Test
    void fluencyMinimumUsesValidRecognizedDuration() {
        var result = new AcousticAssessmentPort.Result(60d, 70d, 8, List.of(), 10_000, 3999);
        var sample = SpeakingAcousticPolicy.sample(10_000, result);
        assertThat(sample.pronunciation()).isEqualTo(60);
        assertThat(sample.fluency()).isNull();
        assertThat(sample.fluencyReason()).isEqualTo("INSUFFICIENT_RECOGNIZED_SPEECH");

        var turns = List.of(sample("a", 8, 6666, 60d, 70d),
                sample("b", 8, 6666, 60d, 70d), sample("c", 8, 6667, 60d, 70d));
        var scores = SpeakingAcousticPolicy.aggregate(turns, false);
        assertThat(scores.pronunciation().getScore()).isEqualTo(60);
        assertThat(scores.fluency().getScore()).isNull();
        assertThat(scores.fluency().getSpeechDurationMs()).isEqualTo(19_999L);
    }

    @Test
    void completeRecognitionRequiresFullPcmConsumption() {
        var segments = List.of(segment("85", "0", "50000000"));
        assertThatThrownBy(() -> AzureSpeakingAssessment.finishRecognition(segments, false))
                .isInstanceOf(AcousticAssessmentPort.Failure.class).hasMessage("AUDIO_PROVIDER_INCOMPLETE");
        assertThat(AzureSpeakingAssessment.finishRecognition(segments, true).fluency()).isEqualTo(85);
    }

    @Test
    void rejectsTheFiftyFirstRecognitionSegment() {
        var azure = new AzureSpeakingAssessment(new SpeakingProviderProperties(), JsonMapper.builder().build());
        var segments = new java.util.ArrayList<AzureSpeakingAssessment.Segment>();
        for (int count = 0; count < 50; count++) {
            azure.addSegment(segments, segmentJson("85", "0", "50000000"), 1_200_000_000);
        }
        assertThatThrownBy(() -> azure.addSegment(segments, segmentJson("85", "0", "50000000"), 1_200_000_000))
                .isInstanceOf(AcousticAssessmentPort.Failure.class).hasMessage("INVALID_PROVIDER_RESULT");
        assertThat(AzureSpeakingAssessment.finishRecognition(segments, true).fluencyEvidenceDurationMs()).isEqualTo(5000);
    }

    private static AzureSpeakingAssessment.Segment segment(String fluency, String offset, String duration) {
        return AzureSpeakingAssessment.parse(JsonMapper.builder().build(), segmentJson(fluency, offset, duration), 1_200_000_000);
    }

    private static String segmentJson(String fluency, String offset, String duration) {
        return """
                {"Offset": %s, "Duration": %s, "NBest": [{"PronunciationAssessment": {"AccuracyScore": 75, "FluencyScore": %s},
                "Words": [{"Word": "hello"}]}]}
                """.formatted(offset, duration, fluency);
    }

    @Test
    void validatesLanguageComponentsAndExamplesIndependently() {
        var history = List.of(SpeakingTurnVo.builder().turnId("t1")
                .transcript("Yesterday I go to school").build());
        var raw = """
                {"grammar":{"accuracy":{"status":"ASSESSED","level":4,"reasonVi":"Câu rõ nghĩa."},
                "range":{"status":"ASSESSED","level":3,"reasonVi":"Mẫu câu khá đa dạng."},
                "examples":[{"turnId":"t1","original":"Yesterday I go to school","suggested":"Yesterday I went to school"},
                {"turnId":"t1","original":"fake sentence","suggested":"invented"}]},
                "vocabulary":{"accuracy":{"status":"ASSESSED","level":4,"reasonVi":"Từ phù hợp."},
                "range":{"status":"ASSESSED","level":"3","reasonVi":"Có thể mở rộng vốn từ."},"examples":[]},
                "feedback":{"strengthsVi":["Bạn diễn đạt rõ ý."],"improvementsVi":[],"recommendationsVi":[]},
                "overallScore":100}
                """;
        var parsed = SpeakingLanguageRubric.parse(JsonMapper.builder().build(), raw, history, 5, 50);
        assertThat(parsed.getGrammar().getScore()).isEqualTo(3.5);
        assertThat(parsed.getGrammar().getExamples()).hasSize(1);
        assertThat(parsed.getVocabulary().getAccuracy().getLevel()).isEqualTo(4);
        assertThat(parsed.getVocabulary().getRange().getLevel()).isNull();
        assertThat(parsed.getVocabulary().getScore()).isNull();
        assertThat(parsed.getFeedback().getStrengthsVi()).containsExactly("Bạn diễn đạt rõ ý.");
    }

    private SpeakingAudioAssessment sample(String id, int words, long duration, Double pronunciation, Double fluency) {
        return SpeakingAudioAssessment.builder().id(id).words(words).durationMs(120_000)
                .assessedSpeechDurationMs(duration).fluencyEvidenceDurationMs(duration)
                .pronunciation(pronunciation).fluency(fluency).build();
    }

    static byte[] phrase(int durationMs, int pauseMs, int firstSpeechMs, int trailingMs) {
        var pcm = ByteBuffer.allocate(durationMs * 32).order(ByteOrder.LITTLE_ENDIAN);
        for (int sample = 0; sample < durationMs * 16; sample++) {
            int ms = sample / 16;
            boolean voiced = ms >= 1000 && ms < 1000 + firstSpeechMs
                    || ms >= 1000 + firstSpeechMs + pauseMs && ms < durationMs - trailingMs;
            double envelope = 0.45 + 0.55 * Math.sin(sample * 2 * Math.PI * 4 / 16000.0);
            pcm.putShort(voiced ? (short) (3500 * envelope * Math.sin(sample * 2 * Math.PI * 200 / 16000.0)) : 0);
        }
        return pcm.array();
    }
}
