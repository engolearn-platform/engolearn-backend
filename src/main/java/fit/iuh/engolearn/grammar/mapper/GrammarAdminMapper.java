package fit.iuh.engolearn.grammar.mapper;

import fit.iuh.engolearn.grammar.dto.request.GrammarContentBlockRequest;
import fit.iuh.engolearn.grammar.dto.request.GrammarContentExampleRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarManagementLessonResponse;
import fit.iuh.engolearn.grammar.dto.request.GrammarLessonRequest;
import fit.iuh.engolearn.grammar.dto.request.GrammarContentQuestionRequest;
import fit.iuh.engolearn.grammar.dto.response.GrammarManagementTopicResponse;
import fit.iuh.engolearn.grammar.model.GrammarLesson;
import fit.iuh.engolearn.grammar.model.GrammarTopic;
import fit.iuh.engolearn.grammar.model.enums.GrammarBlockType;
import fit.iuh.engolearn.grammar.model.enums.GrammarQuestionType;
import fit.iuh.engolearn.grammar.model.enums.GrammarStatus;
import fit.iuh.engolearn.grammar.model.vo.GrammarBlockVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarExampleVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarLessonContentVo;
import fit.iuh.engolearn.grammar.model.vo.GrammarQuestionVo;
import fit.iuh.engolearn.grammar.utils.GrammarAnswerNormalizer;

import fit.iuh.engolearn.grammar.dto.response.GrammarContentBlockResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarContentExampleResponse;
import fit.iuh.engolearn.grammar.dto.response.GrammarContentQuestionResponse;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class GrammarAdminMapper {
    private GrammarAdminMapper() {
    }

    public static GrammarManagementTopicResponse topic(GrammarTopic topic, boolean includeLessons) {
        long lessonCount = topic.getLessons().stream().filter(GrammarAdminMapper::active).count();
        long readyCount = topic.getLessons().stream().filter(GrammarAdminMapper::active)
                .filter(GrammarLesson::isReady).count();
        String status = topic.getStatus() == GrammarStatus.DRAFT && readyCount > 0
                ? "IN_PROGRESS" : topic.getStatus().name();
        List<GrammarManagementLessonResponse> lessons = includeLessons ? topic.getLessons().stream()
                .sorted(Comparator.comparing(GrammarLesson::getOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(GrammarAdminMapper::lesson).toList() : List.of();
        return new GrammarManagementTopicResponse(topic.getId(), topic.getCode(), topic.getTitleVi(), topic.getTitleEn(),
                topic.getDescription(), topic.getLevel().name(), topic.getOrder(), status, topic.getVersion(),
                topic.getUpdatedAt(), topic.getFirstPublishedAt(), readyCount, lessonCount, lessons);
    }

    public static GrammarManagementLessonResponse lesson(GrammarLesson lesson) {
        GrammarLessonRequest input = lessonInput(lesson);
        boolean hasDraft = lesson.getDraft() != null && (lesson.getStatus() == GrammarStatus.PUBLISHED
                || lesson.getStatusBeforeArchive() == GrammarStatus.PUBLISHED);
        return new GrammarManagementLessonResponse(lesson.getId(), lesson.getCode(), lesson.getOrder(), lesson.getStatus().name(),
                lesson.getVersion(), lesson.isReady(), lesson.getContentVersion(), input.titleVi(), input.titleEn(),
                input.objective(), input.tags(), input.blocks().stream().map(GrammarAdminMapper::blockResponse).toList(),
                input.questions().stream().map(GrammarAdminMapper::questionResponse).toList(), hasDraft, lesson.getFirstPublishedAt(),
                lesson.getArchiveReason() == null ? null : lesson.getArchiveReason().name());
    }

    public static GrammarLessonRequest lessonInput(GrammarLesson lesson) {
        GrammarLessonContentVo content = workingContent(lesson);
        return new GrammarLessonRequest(lesson.getVersion(), lesson.getOrder(), content.getTitleVi(), content.getTitleEn(),
                content.getObjective(), content.getTags(), content.getBlocks().stream().map(GrammarAdminMapper::block)
                .toList(), content.getQuestions().stream().map(GrammarAdminMapper::question).toList());
    }

    public static GrammarLessonContentVo workingContent(GrammarLesson lesson) {
        return lesson.getDraft() == null ? lesson.getPublished() : lesson.getDraft();
    }

    public static GrammarLessonContentVo content(String titleVi, String titleEn, String objective, List<String> tags,
                                                  List<GrammarContentBlockRequest> blocks, List<GrammarContentQuestionRequest> questions) {
        return GrammarLessonContentVo.builder().titleVi(titleVi.strip()).titleEn(titleEn.strip())
                .objective(objective.strip()).tags(strings(tags))
                .blocks(list(blocks).stream().map(GrammarAdminMapper::blockContent).toList())
                .questions(list(questions).stream().map(GrammarAdminMapper::questionContent).toList())
                .build();
    }

    private static GrammarContentBlockResponse blockResponse(GrammarContentBlockRequest block) {
        return new GrammarContentBlockResponse(block.id(), block.type(), block.title(), block.text(), block.formula(),
                block.examples().stream().map(example -> new GrammarContentExampleResponse(example.english(),
                        example.vietnamese(), example.analysis())).toList(),
                block.ruleItems(), block.incorrect(), block.correct(), block.explanation());
    }

    private static GrammarContentQuestionResponse questionResponse(GrammarContentQuestionRequest question) {
        return new GrammarContentQuestionResponse(question.id(), question.type(), question.prompt(),
                question.explanation(), question.options(), question.correctIndex(), question.correctAnswer(),
                question.acceptedAnswers());
    }

    private static boolean active(GrammarLesson lesson) {
        return lesson.getStatus() != GrammarStatus.ARCHIVED;
    }

    private static GrammarContentBlockRequest block(GrammarBlockVo value) {
        return new GrammarContentBlockRequest(value.getId(), value.getType().name(), value.getTitle(), value.getText(), value.getFormula(),
                list(value.getExamples()).stream().map(example -> new GrammarContentExampleRequest(orEmpty(example.getEnglish()),
                        orEmpty(example.getVietnamese()), example.getAnalysis())).toList(),
                list(value.getRuleItems()), value.getIncorrect(), value.getCorrect(), value.getExplanation());
    }

    private static GrammarBlockVo blockContent(GrammarContentBlockRequest block) {
        GrammarBlockType type = GrammarBlockType.valueOf(block.type());
        var content = GrammarBlockVo.builder().id(id(block.id())).type(type);
        switch (type) {
            case EXPLANATION -> content.title(text(block.title())).text(text(block.text()));
            case FORMULA -> content.formula(text(block.formula())).text(text(block.text()));
            case EXAMPLE -> content.examples(list(block.examples()).stream().map(example ->
                    GrammarExampleVo.builder().english(text(example.english())).vietnamese(text(example.vietnamese()))
                            .analysis(text(example.analysis())).build()).toList());
            case RULE_QUICK_NOTE -> content.title(text(block.title())).text(text(block.text()))
                    .ruleItems(strings(block.ruleItems()));
            case COMMON_MISTAKE -> content.incorrect(text(block.incorrect())).correct(text(block.correct()))
                    .explanation(text(block.explanation()));
        }
        return content.build();
    }

    private static GrammarContentQuestionRequest question(GrammarQuestionVo value) {
        return new GrammarContentQuestionRequest(value.getId(), value.getType().name(), orEmpty(value.getPrompt()),
                orEmpty(value.getExplanation()), list(value.getOptions()), value.getCorrectIndex(),
                orEmpty(value.getCorrectAnswer()), list(value.getAcceptedAnswers()));
    }

    private static GrammarQuestionVo questionContent(GrammarContentQuestionRequest question) {
        GrammarQuestionType type = GrammarQuestionType.valueOf(question.type());
        var content = GrammarQuestionVo.builder().id(id(question.id())).type(type).prompt(text(question.prompt()))
                .explanation(text(question.explanation()));
        if (type == GrammarQuestionType.MULTIPLE_CHOICE) {
            return content.options(list(question.options()).stream().map(option -> option == null ? "" : option.strip())
                    .toList()).correctIndex(question.correctIndex()).build();
        }
        String answer = text(question.correctAnswer());
        Set<String> seen = new HashSet<>();
        seen.add(GrammarAnswerNormalizer.normalize(answer));
        seen.add("");
        return content.correctAnswer(answer).acceptedAnswers(strings(question.acceptedAnswers()).stream()
                .filter(candidate -> seen.add(GrammarAnswerNormalizer.normalize(candidate))).toList()).build();
    }

    private static List<String> strings(List<String> values) {
        return list(values).stream().map(GrammarAdminMapper::text).filter(Objects::nonNull).toList();
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String id(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.strip();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
