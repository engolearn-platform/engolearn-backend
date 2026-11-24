package fit.iuh.engolearn.speaking.utils;

import fit.iuh.engolearn.speaking.model.SpeakingAudioAssessment;
import fit.iuh.engolearn.speaking.model.vo.SpeakingTurnVo;
import fit.iuh.engolearn.speaking.utils.port.AcousticAssessmentPort;
import fit.iuh.engolearn.speaking.utils.port.LanguageAssessmentPort;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
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
        var longPcm = phrase(42_000, 1000, 16_000, 1500);
        assertThat(SpeakingAcousticPolicy.prepare(longPcm, 120_000).reason()).isEqualTo("AUDIO_LENGTH_UNSUPPORTED");
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
        assertThat(SpeakingAcousticPolicy.sample(6000, new AcousticAssessmentPort.Result(0d, 0d, 5, List.of()))
                .pronunciation()).isZero();
        var shortSample = SpeakingAcousticPolicy.sample(3200, new AcousticAssessmentPort.Result(0d, 0d, 3, List.of()));
        assertThat(shortSample.pronunciation()).isZero();
        assertThat(shortSample.fluency()).isNull();
        assertThat(SpeakingAcousticPolicy.sample(9000, new AcousticAssessmentPort.Result(-1d, 101d, 8, List.of()))
                .pronunciation()).isNull();
    }

    @Test
    void mapsOnlyAzureAccuracyFluencyAndRecognizedWordEvidence() {
        var raw = """
                {"NBest":[{"PronunciationAssessment":{"AccuracyScore":0,"FluencyScore":92,"PronScore":99,"CompletenessScore":100},
                "Words":[{"Word":"yesterday","PronunciationAssessment":{"AccuracyScore":47,"ErrorType":"Mispronunciation"}},
                {"Word":"ghost","PronunciationAssessment":{"AccuracyScore":200,"ErrorType":"Omission"}},
                {"Word":"hello","PronunciationAssessment":{"AccuracyScore":71,"ErrorType":"None"}}]}]}
                """;
        var mapped = AzureSpeakingAssessment.parse(JsonMapper.builder().build(), raw);
        assertThat(mapped.pronunciation()).isZero();
        assertThat(mapped.fluency()).isEqualTo(92);
        assertThat(mapped.words()).isEqualTo(2);
        assertThat(mapped.feedback()).extracting(word -> word.getWord()).containsExactly("yesterday", "hello");
    }

    @Test
    void preservesWordWeightedNativeAccuracyAcrossSegmentsWithoutInferringFluency() {
        var combined = AzureSpeakingAssessment.combine(List.of(
                new AcousticAssessmentPort.Result(40d, 20d, 3, List.of()),
                new AcousticAssessmentPort.Result(100d, 80d, 7, List.of())));
        var sample = SpeakingAcousticPolicy.sample(9000, combined);
        assertThat(sample.pronunciation()).isEqualTo(82);
        assertThat(sample.fluency()).isNull();
        assertThat(sample.fluencyReason()).isEqualTo("AUDIO_SEGMENTED_FLUENCY_UNAVAILABLE");
        assertThat(sample.words()).isEqualTo(10);
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
        return SpeakingAudioAssessment.builder().id(id).words(words).durationMs(duration)
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
