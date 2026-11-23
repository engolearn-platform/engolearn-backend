package fit.iuh.engolearn.grammar.service;

import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Block;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Check;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.LessonInput;
import fit.iuh.engolearn.grammar.dto.GrammarAdminDtos.Question;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarChecklistValidatorTest {
    private final GrammarChecklistValidator validator = new GrammarChecklistValidator();

    @Test
    void readyMeansAValidLessonMeetsAllFourteenRules() {
        var checklist = validator.lesson(lesson("She ____ to school.", "walks"));
        assertThat(checklist.checks()).hasSize(14).allMatch(Check::passed);
    }

    @Test
    void fillInBlankRejectsWrongPlaceholderCountAndPunctuationOnlyAnswers() {
        assertThat(validator.lesson(lesson("She ____ to ____.", "walks")).checks().get(12).passed()).isFalse();
        assertThat(validator.lesson(lesson("She _____ to school.", "walks")).checks().get(12).passed()).isFalse();
        assertThat(validator.lesson(lesson("She ____ to school.", "...")).checks().get(12).passed()).isFalse();
    }

    @Test
    void emptyTopicIsValidDraftButFailsThePublicationChecklist() {
        var topic = GrammarTopic.builder().titleVi("Thì hiện tại đơn").titleEn("Present simple").level(GrammarLevel.A1)
                .description("Mô tả đủ độ dài để biên tập viên giới thiệu chủ đề và mục tiêu học tập cho người dùng.")
                .lessons(List.of()).build();
        var checks = validator.topic(topic).checks();
        assertThat(checks).hasSize(7);
        assertThat(checks.get(0).passed()).isTrue();
        assertThat(checks.get(1).passed()).isFalse();
    }

    private LessonInput lesson(String prompt, String answer) {
        var explanation = new Block("b", "EXPLANATION", "Hiện tại đơn", "Diễn tả thói quen.", null,
                List.of(), List.of(), null, null, null);
        List<Question> questions = IntStream.range(0, 5).mapToObj(index ->
                new Question("q" + index, "FILL_IN_BLANK", prompt, "She là ngôi thứ ba số ít.",
                        List.of(), null, answer, List.of())).toList();
        return new LessonInput(0L, 1, "Thói quen", "Habits", "Dùng đúng hiện tại đơn.",
                List.of(), List.of(explanation), questions);
    }
}
