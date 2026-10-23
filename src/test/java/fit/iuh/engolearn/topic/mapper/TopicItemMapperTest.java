package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.TopicContextResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionBlockResponse;
import fit.iuh.engolearn.topic.dto.response.TopicExpressionExampleResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicQuickCheckResponse;
import fit.iuh.engolearn.topic.dto.response.TopicVocabularyResponse;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import fit.iuh.engolearn.topic.model.vo.DialogueTurnVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.QuickCheckVo;
import fit.iuh.engolearn.topic.model.vo.QuizSentenceVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.model.vo.WordSampleVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopicItemMapperTest {

    @Test
    void contentResponseMapsEveryPartOfTheVersion() {
        TopicItemVersion version = TopicItemVersion.builder()
                .id("iv1")
                .version(3)
                .title("At the airport")
                .description("Buy a ticket")
                .context(TopicContextVo.builder()
                        .scenarioDescription("At the airport")
                        .prompt("Where do you go?")
                        .dialogue(List.of(DialogueTurnVo.builder()
                                .speaker("Clerk")
                                .textEn("Hello")
                                .textVi("Xin chao")
                                .audioUrl("hello.mp3")
                                .build()))
                        .durationSec(90)
                        .tips("Speak slowly")
                        .build())
                .vocabularies(List.of(ItemWordVo.builder()
                        .vocabId("v1")
                        .senseId("s1")
                        .snapshot(WordSnapshotVo.builder()
                                .word("airport")
                                .phonetic("/airport/")
                                .describeVi("san bay")
                                .pos("noun")
                                .audioUrl("airport.mp3")
                                .audioEnUrl("airport_en.mp3")
                                .audioUsUrl("airport_us.mp3")
                                .build())
                        .samples(List.of(WordSampleVo.builder()
                                .textEn("The airport is far")
                                .textVi("San bay rat xa")
                                .audioUrl("sample.mp3")
                                .build()))
                        .quickCheck(QuickCheckVo.builder()
                                .quizData(Map.of("question", "Fill in the blank", "options", List.of("a", "b")))
                                .answerData(Map.of("answer", "airport"))
                                .build())
                        .orderIndex(1)
                        .tips("word tips")
                        .build()))
                .expressions(List.of(ExpressionBlockVo.builder()
                        .purposes(List.of(ExpressionPurposeVo.builder()
                                .purposeEn("Ask for a ticket")
                                .purposeVi("Hoi mua ve")
                                .orderIndex(1)
                                .examples(List.of(ExpressionExampleVo.builder()
                                        .type(ExpressionType.POLITE_INQUIRY)
                                        .textEn("Could I have a ticket?")
                                        .textVi("Cho toi mot ve duoc khong?")
                                        .usageNote("Polite")
                                        .audioUrl("expression.mp3")
                                        .build()))
                                .build()))
                        .tips("expression tips")
                        .build()))
                .build();

        TopicItemContentResponse response = TopicItemMapper.toContentResponse("i1", version);

        assertThat(response.getTopicItemId()).isEqualTo("i1");
        assertThat(response.getTopicItemVersionId()).isEqualTo("iv1");
        assertThat(response.getVersion()).isEqualTo(3);
        assertThat(response.getTitle()).isEqualTo("At the airport");
        assertThat(response.getDescription()).isEqualTo("Buy a ticket");
        assertThat(response.getContext().getScenarioDescription()).isEqualTo("At the airport");
        assertThat(response.getContext().getPromptingQuestion()).isEqualTo("Where do you go?");
        assertThat(response.getContext().getDurationSec()).isEqualTo(90);
        assertThat(response.getContext().getTips()).isEqualTo("Speak slowly");
        assertThat(response.getContext().getDialogue()).singleElement().satisfies(turn -> {
            assertThat(turn.getSpeaker()).isEqualTo("Clerk");
            assertThat(turn.getTextEn()).isEqualTo("Hello");
            assertThat(turn.getTextVi()).isEqualTo("Xin chao");
            assertThat(turn.getAudioUrl()).isEqualTo("hello.mp3");
        });
        assertThat(response.getVocabularies()).singleElement().satisfies(vocabulary -> {
            assertThat(vocabulary.getVocabId()).isEqualTo("v1");
            assertThat(vocabulary.getSenseId()).isEqualTo("s1");
            assertThat(vocabulary.getWord()).isEqualTo("airport");
            assertThat(vocabulary.getPronunciation()).isEqualTo("/airport/");
            assertThat(vocabulary.getMeaningVi()).isEqualTo("san bay");
            assertThat(vocabulary.getPartOfSpeech()).isEqualTo("noun");
            assertThat(vocabulary.getAudioUrl()).isEqualTo("airport.mp3");
            assertThat(vocabulary.getAudioEn()).isEqualTo("airport_en.mp3");
            assertThat(vocabulary.getAudioUs()).isEqualTo("airport_us.mp3");
            assertThat(vocabulary.getOrderIndex()).isEqualTo(1);
            assertThat(vocabulary.getTips()).isEqualTo("word tips");
            assertThat(vocabulary.getExamples()).singleElement().satisfies(example -> {
                assertThat(example.getTextEn()).isEqualTo("The airport is far");
                assertThat(example.getTextVi()).isEqualTo("San bay rat xa");
                assertThat(example.getAudioUrl()).isEqualTo("sample.mp3");
            });
            assertThat(vocabulary.getQuickCheck().getQuestion()).isEqualTo("Fill in the blank");
            assertThat(vocabulary.getQuickCheck().getOptions()).containsExactly("a", "b");
            assertThat(vocabulary.getQuickCheck().getCorrectAnswer()).isEqualTo("airport");
        });
        assertThat(response.getExpressions()).singleElement().satisfies(block -> {
            assertThat(block.getTips()).isEqualTo("expression tips");
            assertThat(block.getPurposes()).singleElement().satisfies(purpose -> {
                assertThat(purpose.getPurposeEn()).isEqualTo("Ask for a ticket");
                assertThat(purpose.getPurposeVi()).isEqualTo("Hoi mua ve");
                assertThat(purpose.getOrderIndex()).isEqualTo(1);
                assertThat(purpose.getExamples()).singleElement().satisfies(example -> {
                    assertThat(example.getType()).isEqualTo(ExpressionType.POLITE_INQUIRY);
                    assertThat(example.getTextEn()).isEqualTo("Could I have a ticket?");
                    assertThat(example.getTextVi()).isEqualTo("Cho toi mot ve duoc khong?");
                    assertThat(example.getUsageNote()).isEqualTo("Polite");
                    assertThat(example.getAudioUrl()).isEqualTo("expression.mp3");
                });
            });
        });
    }

    @Test
    void contextResponseIsNullWhenTheContextIsMissing() {
        assertThat(TopicItemMapper.toContextResponse(null)).isNull();
    }

    @Test
    void vocabularyWithoutASnapshotHasNullWordFields() {
        TopicVocabularyResponse response = TopicItemMapper.toVocabularyResponse(
                ItemWordVo.builder().vocabId("v1").senseId("s1").build());

        assertThat(response.getVocabId()).isEqualTo("v1");
        assertThat(response.getSenseId()).isEqualTo("s1");
        assertThat(response.getWord()).isNull();
        assertThat(response.getPronunciation()).isNull();
        assertThat(response.getMeaningVi()).isNull();
        assertThat(response.getPartOfSpeech()).isNull();
        assertThat(response.getAudioUrl()).isNull();
        assertThat(response.getUsageDescription()).isNull();
        assertThat(response.getExamples()).isNull();
        assertThat(response.getQuickCheck()).isNull();
    }

    @Test
    void quickCheckResponseIsNullWhenTheQuickCheckIsMissing() {
        assertThat(TopicItemMapper.toQuickCheckResponse(null)).isNull();
    }

    @Test
    void quickCheckIsReadFromTheSchemaKeys() {
        QuickCheckVo quickCheck = QuickCheckVo.builder()
                .quizData(Map.of(
                        "question", "Fill in the blank",
                        "options", List.of("a", "b"),
                        "correct_answer", "airport"))
                .build();

        TopicQuickCheckResponse response = TopicItemMapper.toQuickCheckResponse(quickCheck);

        assertThat(response.getQuestion()).isEqualTo("Fill in the blank");
        assertThat(response.getOptions()).containsExactly("a", "b");
        assertThat(response.getCorrectAnswer()).isEqualTo("airport");
    }

    @Test
    void quickCheckAcceptsCamelCaseAndAlternateAnswerKeys() {
        TopicQuickCheckResponse camelCase = TopicItemMapper.toQuickCheckResponse(QuickCheckVo.builder()
                .quizData(Map.of("options", List.of("a", "b")))
                .answerData(Map.of("correctAnswer", "airport"))
                .build());
        TopicQuickCheckResponse answer = TopicItemMapper.toQuickCheckResponse(QuickCheckVo.builder()
                .quizData(Map.of("answer", "station"))
                .build());

        assertThat(camelCase.getCorrectAnswer()).isEqualTo("airport");
        assertThat(answer.getCorrectAnswer()).isEqualTo("station");
    }

    @Test
    void quickCheckFallsBackToTheSentenceWhenNoQuestionIsStored() {
        TopicQuickCheckResponse response = TopicItemMapper.toQuickCheckResponse(QuickCheckVo.builder()
                .sentence(QuizSentenceVo.builder().textEn("Fill in the blank: the ___").build())
                .quizData(Map.of("options", List.of("a", "b")))
                .build());

        assertThat(response.getQuestion()).isEqualTo("Fill in the blank: the ___");
        assertThat(response.getOptions()).containsExactly("a", "b");
        assertThat(response.getCorrectAnswer()).isNull();
    }

    @Test
    void quickCheckWithoutMapsHasEmptyOptions() {
        TopicQuickCheckResponse response = TopicItemMapper.toQuickCheckResponse(QuickCheckVo.builder().build());

        assertThat(response.getQuestion()).isNull();
        assertThat(response.getOptions()).isEmpty();
        assertThat(response.getCorrectAnswer()).isNull();
    }

    @Test
    void expressionBlockWithEmptyPurposesKeepsAnEmptyList() {
        TopicExpressionBlockResponse response = TopicItemMapper.toExpressionBlockResponse(
                ExpressionBlockVo.builder().purposes(List.of()).tips("tips").build());

        assertThat(response.getPurposes()).isEmpty();
        assertThat(response.getTips()).isEqualTo("tips");
    }

    @Test
    void expressionExampleMapsEveryField() {
        TopicExpressionExampleResponse response = TopicItemMapper.toExpressionExampleResponse(
                ExpressionExampleVo.builder()
                        .type(ExpressionType.SLOT_PATTERN)
                        .textEn("I would like ___")
                        .textVi("Toi muon ___")
                        .usageNote("Use with a noun")
                        .audioUrl("example.mp3")
                        .build());

        assertThat(response.getType()).isEqualTo(ExpressionType.SLOT_PATTERN);
        assertThat(response.getTextEn()).isEqualTo("I would like ___");
        assertThat(response.getTextVi()).isEqualTo("Toi muon ___");
        assertThat(response.getUsageNote()).isEqualTo("Use with a noun");
        assertThat(response.getAudioUrl()).isEqualTo("example.mp3");
    }
}
