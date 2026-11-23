package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ExpressionGroupType;
import fit.iuh.engolearn.topic.model.enums.ExpressionLabel;
import fit.iuh.engolearn.topic.model.enums.ExpressionType;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fit.iuh.engolearn.topic.model.enums.ContentStatus.DRAFT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicPublishReadinessServiceImplTest {
    private final TopicManagementReadService readService = mock(TopicManagementReadService.class);
    private final TopicPublishReadinessServiceImpl service = new TopicPublishReadinessServiceImpl(readService);

    @Test
    void reportsLocalizedFieldPathsAndDoesNotInspectHiddenItems() {
        when(readService.topic("topic-1")).thenReturn(topic(false));
        when(readService.items("topic-1")).thenReturn(List.of(summary("hidden", ContentStatus.HIDDEN)));

        var result = service.readiness("topic-1");

        assertThat(result.ready()).isFalse();
        assertThat(result.violations()).extracting("path").contains("topic.titleEn", "topic.titleVi", "topic.coverUrl", "topic.items");
        verify(readService, never()).item("topic-1", "hidden");
    }

    @Test
    void completeWorkingDraftIsReadyAndQuestionErrorsPointToTheQuestionField() {
        when(readService.topic("topic-1")).thenReturn(topic(true));
        List<TopicManagementItemSummary> summaries = new ArrayList<>();
        for (int i = 0; i < 5; i++) summaries.add(summary("item-" + i, DRAFT));
        when(readService.items("topic-1")).thenReturn(summaries);
        for (var summary : summaries) when(readService.item("topic-1", summary.id())).thenReturn(item(summary.id(), true));

        assertThat(service.readiness("topic-1").ready()).isTrue();

        when(readService.item("topic-1", "item-0")).thenReturn(item("item-0", false));
        var invalid = service.readiness("topic-1");
        assertThat(invalid.ready()).isFalse();
        assertThat(invalid.violations()).anySatisfy(v -> {
            assertThat(v.path()).isEqualTo("items[item-0].quizzes[0].questions[0].sentence.textEn");
            assertThat(v.code()).isEqualTo("REQUIRED");
        });
    }

    private static TopicManagementResponse topic(boolean complete) {
        return new TopicManagementResponse("topic-1", complete ? "Travel" : null, complete ? "Du lịch" : null,
                complete ? CefrLevel.A1 : null, complete ? TopicCategory.TRAVEL : null,
                complete ? "Description" : null, complete ? "https://media/cover.jpg" : null,
                "draft", 0, 0, null, null, List.of(), null, null, null, null, 1L);
    }

    private static TopicManagementItemSummary summary(String id, ContentStatus status) {
        return new TopicManagementItemSummary(id, "topic-1", "Item", null, 0, status, 1,
                "version-" + id, 1, null, null, null, null, null, null);
    }

    private static TopicManagementItemDetail item(String id, boolean complete) {
        List<TopicManagementItemDetail.Vocabulary> words = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            words.add(new TopicManagementItemDetail.Vocabulary("word-" + i, null,
                    new TopicManagementItemDetail.WordSnapshot("word", "noun", "meaning", "nghĩa", "/p/", "audio", null, null),
                    List.of(new TopicManagementItemDetail.WordSample("English", "Tiếng Việt", "audio", true)),
                    List.of(), null, i, null));
        }
        var expression = new TopicManagementItemDetail.ExpressionExample(ExpressionType.POLITE_INQUIRY, "Could you help?", "Bạn giúp tôi?",
                "Polite request", "audio", List.of(), ExpressionLabel.POLITE);
        var group = new TopicManagementItemDetail.ExpressionPurpose("Requests", "Yêu cầu", 0,
                ExpressionGroupType.BASIC, List.of(expression));
        List<TopicManagementItemDetail.QuizModule> quizzes = List.of(
                module(QuizType.FILL_BLANK, "fill", complete),
                module(QuizType.SITUATIONAL_CHOICE, "choice", true));
        return new TopicManagementItemDetail(id, "topic-1", "Item", "Description", 0, DRAFT, 5,
                "version-" + id, 1, null, null, null, null, null, null,
                new TopicManagementItemDetail.Context("Prompt", "Scenario", List.of(), 5, null),
                words, List.of(new TopicManagementItemDetail.ExpressionBlock(List.of(group), null)), quizzes);
    }

    private static TopicManagementItemDetail.QuizModule module(QuizType type, String id, boolean complete) {
        List<TopicManagementItemDetail.QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<TopicManagementItemDetail.QuizOption> options = List.of(
                    new TopicManagementItemDetail.QuizOption("a", "answer", true),
                    new TopicManagementItemDetail.QuizOption("b", "other", false),
                    new TopicManagementItemDetail.QuizOption("c", "another", false),
                    new TopicManagementItemDetail.QuizOption("d", "last", false));
            var sentence = type == QuizType.FILL_BLANK
                    ? new TopicManagementItemDetail.QuizSentence(complete ? "I need an answer today." : null,
                    "Hôm nay tôi cần một câu trả lời.", "audio", null, null, null) : null;
            questions.add(new TopicManagementItemDetail.QuizQuestion(type == QuizType.SITUATIONAL_CHOICE ? "What do you say?" : null,
                    null, options, null, null, null, sentence, Map.of(), Map.of(), "Explanation", null));
        }
        return new TopicManagementItemDetail.QuizModule(id, type, "Quiz", null, 0, questions);
    }
}
