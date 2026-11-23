package fit.iuh.engolearn.topic.mapper;

import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.ChangeType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopicManagementItemMapperTest {

    private final TopicManagementItemMapper mapper = new TopicManagementItemMapper();

    @Test
    void detailMapsIdentityVersionAndEveryEmbeddedResourceInStoredOrder() {
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        TopicItemVersion version = TopicItemVersion.builder()
                .id("version-2")
                .topicItemId("item-1")
                .topicId("topic-1")
                .topicVersionId("topic-version-3")
                .version(2)
                .previousItemVersionId("version-1")
                .changeType(ChangeType.MAJOR)
                .title("At the restaurant")
                .description("A real item")
                .order(4)
                .status(ContentStatus.DRAFT)
                .durationMinutes(8)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .publishedAt(null)
                .context(TopicContextVo.builder()
                        .prompt("What would you order?")
                        .scenarioDescription("A busy cafe")
                        .durationSec(40)
                        .tips("Listen first")
                        .dialogue(List.of(DialogueTurnVo.builder()
                                .speaker("Server").textEn("What can I get you?")
                                .textVi("Bạn dùng gì?").audioUrl("turn.mp3").build()))
                        .build())
                .vocabularies(List.of(
                        ItemWordVo.builder().vocabId("vocab-2").senseId("sense-2").orderIndex(2)
                                .snapshot(WordSnapshotVo.builder().word("menu").pos("noun").sense("list of food")
                                        .describeVi("thực đơn").phonetic("/ˈmenjuː/").audioUrl("menu.mp3")
                                        .audioEnUrl("menu-en.mp3").audioUsUrl("menu-us.mp3").build())
                                .samples(List.of(WordSampleVo.builder().textEn("The menu is here.")
                                        .textVi("Thực đơn ở đây.").audioUrl("sample.mp3").isManual(true).build()))
                                .collocations(List.of(CollocationVo.builder().text("a menu")
                                        .descriptionVi("một thực đơn")
                                        .example(CollocationExampleVo.builder().en("A menu, please.")
                                                .vi("Cho tôi thực đơn.").build()).build()))
                                .quickCheck(QuickCheckVo.builder().sentence(QuizSentenceVo.builder()
                                                .textEn("Choose one.").meaningVi("Chọn một.").audioUrl("quick.mp3")
                                                .audioUrlSlow("quick-slow.mp3").source("local").externalId("ex-1").build())
                                        .quizData(Map.of("prompt", "choose"))
                                        .answerData(Map.of("answer", "menu"))
                                        .explanation("The correct word is menu.").build())
                                .tips("Remember the stress").build(),
                        ItemWordVo.builder().vocabId("vocab-1").orderIndex(1)
                                .snapshot(WordSnapshotVo.builder().word("table").build()).build()))
                .expressions(List.of(ExpressionBlockVo.builder().tips("Use politely")
                        .purposes(List.of(ExpressionPurposeVo.builder().purposeEn("Ordering")
                                .purposeVi("Gọi món").orderIndex(3)
                                .examples(List.of(ExpressionExampleVo.builder().type(ExpressionType.POLITE_INQUIRY)
                                        .textEn("Could I have the soup?").textVi("Cho tôi súp được không?")
                                        .usageNote("Polite request").audioUrl("expression.mp3")
                                        .linkedVocabIds(List.of("vocab-2", "vocab-1")).build()))
                                .build())).build()))
                .quizzes(List.of(QuizModuleVo.builder().quizId("quiz-1").quizType(QuizType.SITUATIONAL_CHOICE)
                        .title("Choose a response").instructions("Pick the best answer.").orderIndex(5)
                        .questions(List.of(QuizQuestionVo.builder().prompt("What do you say?")
                                .audioUrl("question.mp3").correct("B").explanationOk("Correct")
                                .explanationNg("Try again").explanation("Choose the polite option")
                                .tips("Read carefully")
                                .sentence(QuizSentenceVo.builder().textEn("Can I see the menu?")
                                        .meaningVi("Tôi xem thực đơn được không?").audioUrl("sentence.mp3")
                                        .audioUrlSlow("sentence-slow.mp3").source("seed").externalId("sentence-2").build())
                                .quizData(Map.of("situation", "cafe"))
                                .answerData(Map.of("correct", "B"))
                                .options(List.of(QuizOptionVo.builder().label("A").textEn("No").isCorrect(false).build(),
                                        QuizOptionVo.builder().label("B").textEn("Certainly").isCorrect(true).build()))
                                .build())).build()))
                .build();

        TopicManagementItemDetail detail = mapper.detail(version);

        assertThat(detail.id()).isEqualTo("item-1");
        assertThat(detail.versionId()).isEqualTo("version-2");
        assertThat(detail.topicVersionId()).isEqualTo("topic-version-3");
        assertThat(detail.previousItemVersionId()).isEqualTo("version-1");
        assertThat(detail.changeType()).isEqualTo(ChangeType.MAJOR);
        assertThat(detail.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(detail.createdAt()).isEqualTo(createdAt);
        assertThat(detail.context().dialogue()).extracting(TopicManagementItemDetail.DialogueTurn::textEn)
                .containsExactly("What can I get you?");
        assertThat(detail.vocabularies()).extracting(vocabulary -> vocabulary.snapshot().word())
                .containsExactly("menu", "table");
        assertThat(detail.vocabularies().getFirst().quickCheck().answerData()).containsEntry("answer", "menu");
        assertThat(detail.vocabularies().getFirst().samples().getFirst().isManual()).isTrue();
        assertThat(detail.expressions().getFirst().purposes().getFirst().examples().getFirst().linkedVocabIds())
                .containsExactly("vocab-2", "vocab-1");
        assertThat(detail.quizzes().getFirst().questions().getFirst().correct()).isEqualTo("B");
        assertThat(detail.quizzes().getFirst().questions().getFirst().options())
                .extracting(TopicManagementItemDetail.QuizOption::isCorrect).containsExactly(false, true);
    }

    @Test
    void summaryUsesStableItemIdAndDetailNormalizesMissingResourceArrays() {
        TopicItemVersion version = TopicItemVersion.builder()
                .id("version-1")
                .topicItemId("stable-item")
                .topicId("topic-1")
                .status(ContentStatus.PUBLISHED)
                .build();

        TopicManagementItemSummary summary = mapper.summary(version);
        TopicManagementItemDetail detail = mapper.detail(version);

        assertThat(summary.id()).isEqualTo("stable-item");
        assertThat(summary.versionId()).isEqualTo("version-1");
        assertThat(summary.title()).isNull();
        assertThat(detail.context()).isNull();
        assertThat(detail.vocabularies()).isEmpty();
        assertThat(detail.expressions()).isEmpty();
        assertThat(detail.quizzes()).isEmpty();
    }
}
