package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse.Violation;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.enums.TopicMediaKind;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import fit.iuh.engolearn.topic.service.TopicPublishReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Checks presence of media references; legacy URLs do not retain enough information to verify file type or size. */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class TopicPublishReadinessServiceImpl implements TopicPublishReadinessService {
    private final TopicManagementReadService readService;
    private final TopicRepository topics;
    private final TopicMediaService media;

    @Override
    public TopicPublishReadinessResponse readiness(String topicId) {
        TopicManagementResponse topic = readService.topic(topicId);
        Topic entity = topics.findById(topicId).orElse(null);
        List<Violation> violations = new ArrayList<>();
        required(violations, "topic.titleEn", topic.titleEn());
        required(violations, "topic.titleVi", topic.titleVi());
        required(violations, "topic.cefr", topic.cefr());
        required(violations, "topic.category", topic.category());
        required(violations, "topic.description", topic.description());
        validateMedia(violations, entity, "topic.coverUrl", topic.coverUrl(), TopicMediaKind.IMAGE, true);

        List<TopicManagementItemSummary> visible = readService.items(topicId).stream()
                .filter(item -> item.status() != ContentStatus.HIDDEN).toList();
        if (visible.size() < 5 || visible.size() > 20) {
            add(violations, "topic.items", "ITEM_COUNT", "Topic must have between 5 and 20 visible items.");
        }
        for (TopicManagementItemSummary summary : visible) {
            TopicManagementItemDetail item = readService.item(topicId, summary.id());
            required(violations, "items[" + summary.id() + "].order", summary.order());
            validateItem(item, violations, entity);
        }
        return new TopicPublishReadinessResponse(violations.isEmpty(), violations);
    }

    private void validateItem(TopicManagementItemDetail item, List<Violation> violations, Topic topic) {
        String base = "items[" + item.id() + "]";
        required(violations, base + ".title", item.title());
        required(violations, base + ".durationMinutes", item.durationMinutes());
        if (item.context() == null) {
            add(violations, base + ".context.scenarioDescription", "REQUIRED", "Context scenario description is required.");
        } else {
            required(violations, base + ".context.scenarioDescription", item.context().scenarioDescription());
        }
        if (item.context() != null) {
            List<TopicManagementItemDetail.DialogueTurn> dialogue = safe(item.context().dialogue());
            for (int i = 0; i < dialogue.size(); i++) {
                var turn = dialogue.get(i);
                if (turn != null) validateMedia(violations, topic, base + ".context.dialogue[" + i + "].audioUrl",
                        turn.audioUrl(), TopicMediaKind.AUDIO, false);
            }
        }
        validateVocabulary(item, base, violations, topic);
        validateExpressions(item, base, violations, topic);
        validateQuizzes(item, base, violations, topic);
    }

    private void validateVocabulary(TopicManagementItemDetail item, String base, List<Violation> violations, Topic topic) {
        List<TopicManagementItemDetail.Vocabulary> words = safe(item.vocabularies());
        count(violations, base + ".vocabularies", words.size(), 3, 5, "Vocabulary count must be between 3 and 5.");
        for (int i = 0; i < words.size(); i++) {
            var word = words.get(i);
            String path = base + ".vocabularies[" + i + "]";
            if (word == null) {
                add(violations, path, "REQUIRED", "Vocabulary entry is required.");
                continue;
            }
            var snapshot = word.snapshot();
            if (snapshot == null) {
                add(violations, path + ".snapshot", "REQUIRED", "Vocabulary word snapshot is required.");
            } else {
                required(violations, path + ".snapshot.word", snapshot.word());
                required(violations, path + ".snapshot.phonetic", snapshot.phonetic());
                required(violations, path + ".snapshot.describeVi", snapshot.describeVi());
                validateMedia(violations, topic, path + ".snapshot.audioEnUrl", snapshot.audioEnUrl(), TopicMediaKind.AUDIO, true);
                validateMedia(violations, topic, path + ".snapshot.audioUsUrl", snapshot.audioUsUrl(), TopicMediaKind.AUDIO, true);
                validateMedia(violations, topic, path + ".snapshot.audioUrl", snapshot.audioUrl(), TopicMediaKind.AUDIO, false);
            }
            List<TopicManagementItemDetail.Collocation> collocations = safe(word.collocations());
            count(violations, path + ".collocations", collocations.size(), 0, 5,
                    "A vocabulary entry may have at most 5 collocations.");
            List<TopicManagementItemDetail.WordSample> examples = safe(word.samples());
            count(violations, path + ".examples", examples.size(), 1, 3,
                    "A vocabulary entry must have between 1 and 3 examples.");
            for (int j = 0; j < examples.size(); j++) {
                var example = examples.get(j);
                String examplePath = path + ".examples[" + j + "]";
                if (example == null) {
                    add(violations, examplePath, "REQUIRED", "Vocabulary example is required.");
                    continue;
                }
                required(violations, examplePath + ".textEn", example.textEn());
                required(violations, examplePath + ".textVi", example.textVi());
                validateMedia(violations, topic, examplePath + ".audioUrl", example.audioUrl(), TopicMediaKind.AUDIO, true);
            }
            if (word.quickCheck() != null && word.quickCheck().sentence() != null) {
                var sentence = word.quickCheck().sentence();
                validateMedia(violations, topic, path + ".quickCheck.sentence.audioUrl",
                        sentence.audioUrl(), TopicMediaKind.AUDIO, false);
                validateMedia(violations, topic, path + ".quickCheck.sentence.audioUrlSlow",
                        sentence.audioUrlSlow(), TopicMediaKind.AUDIO, false);
            }
        }
    }

    private void validateExpressions(TopicManagementItemDetail item, String base, List<Violation> violations, Topic topic) {
        List<TopicManagementItemDetail.ExpressionPurpose> groups = safe(item.expressions()).stream()
                .filter(block -> block != null).flatMap(block -> safe(block.purposes()).stream()).toList();
        count(violations, base + ".expressionGroups", groups.size(), 1, 3,
                "An item must have between 1 and 3 expression groups.");
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            String path = base + ".expressionGroups[" + i + "]";
            if (group == null) {
                add(violations, path, "REQUIRED", "Expression group is required.");
                continue;
            }
            if (group.groupType() == null) add(violations, path + ".groupType", "REQUIRED", "Expression group type is required.");
            List<TopicManagementItemDetail.ExpressionExample> expressions = safe(group.examples());
            count(violations, path + ".expressions", expressions.size(), 1, 3,
                    "An expression group must have between 1 and 3 expressions.");
            for (int j = 0; j < expressions.size(); j++) {
                var expression = expressions.get(j);
                String expressionPath = path + ".expressions[" + j + "]";
                if (expression == null) {
                    add(violations, expressionPath, "REQUIRED", "Expression is required.");
                    continue;
                }
                required(violations, expressionPath + ".label", expression.label());
                required(violations, expressionPath + ".textEn", expression.textEn());
                required(violations, expressionPath + ".textVi", expression.textVi());
                required(violations, expressionPath + ".usageNote", expression.usageNote());
                validateMedia(violations, topic, expressionPath + ".audioUrl", expression.audioUrl(), TopicMediaKind.AUDIO, true);
            }
        }
    }

    private void validateQuizzes(TopicManagementItemDetail item, String base, List<Violation> violations, Topic topic) {
        Map<QuizType, List<QuestionRef>> byType = new EnumMap<>(QuizType.class);
        List<TopicManagementItemDetail.QuizModule> modules = safe(item.quizzes());
        for (int m = 0; m < modules.size(); m++) {
            var module = modules.get(m);
            String modulePath = base + ".quizzes[" + m + "]";
            if (module == null) {
                add(violations, modulePath, "REQUIRED", "Quiz module is required.");
                continue;
            }
            if (module.quizType() != QuizType.FILL_BLANK && module.quizType() != QuizType.SITUATIONAL_CHOICE) {
                add(violations, modulePath + ".quizType", "UNSUPPORTED", "Only FILL_BLANK and SITUATIONAL_CHOICE quizzes are supported.");
                continue;
            }
            required(violations, modulePath + ".title", module.title());
            List<TopicManagementItemDetail.QuizQuestion> questions = safe(module.questions());
            List<QuestionRef> refs = byType.computeIfAbsent(module.quizType(), ignored -> new ArrayList<>());
            for (int q = 0; q < questions.size(); q++) {
                if (questions.get(q) == null) add(violations, modulePath + ".questions[" + q + "]", "REQUIRED", "Quiz question is required.");
                else {
                    String questionPath = modulePath + ".questions[" + q + "]";
                    var question = questions.get(q);
                    refs.add(new QuestionRef(question, questionPath));
                    validateMedia(violations, topic, questionPath + ".audioUrl", question.audioUrl(), TopicMediaKind.AUDIO, false);
                    if (question.sentence() != null) {
                        validateMedia(violations, topic, questionPath + ".sentence.audioUrl", question.sentence().audioUrl(), TopicMediaKind.AUDIO, false);
                        validateMedia(violations, topic, questionPath + ".sentence.audioUrlSlow", question.sentence().audioUrlSlow(), TopicMediaKind.AUDIO, false);
                    }
                }
            }
        }
        for (QuizType type : List.of(QuizType.FILL_BLANK, QuizType.SITUATIONAL_CHOICE)) {
            List<QuestionRef> questions = byType.getOrDefault(type, List.of());
            String path = base + ".quizzes." + type;
            count(violations, path, questions.size(), 3, 5, type + " must contain between 3 and 5 questions.");
            for (QuestionRef ref : questions) validateQuestion(type, ref, violations);
        }
    }

    private void validateQuestion(QuizType type, QuestionRef ref, List<Violation> violations) {
        var question = ref.question();
        String path = ref.path();
        required(violations, path + ".explanation", question.explanation());
        List<TopicManagementItemDetail.QuizOption> options = safe(question.options());
        int correctCount = (int) options.stream().filter(option -> option != null && Boolean.TRUE.equals(option.isCorrect())).count();
        if (correctCount != 1) add(violations, path + ".options", "CORRECT_ANSWER_COUNT", "Question must have exactly one correct answer.");
        if (type == QuizType.FILL_BLANK) {
            count(violations, path + ".wordBank", options.size(), 4, 6, "Fill-blank word bank must contain between 4 and 6 words.");
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i) == null || blank(options.get(i).textEn())) {
                    add(violations, path + ".wordBank[" + i + "].textEn", "REQUIRED", "Word-bank word is required.");
                }
            }
            if (question.sentence() == null) {
                add(violations, path + ".sentence.textEn", "REQUIRED", "Completed sentence is required.");
            } else {
                required(violations, path + ".sentence.textEn", question.sentence().textEn());
                if (correctCount == 1) {
                    var correct = options.stream().filter(option -> option != null && Boolean.TRUE.equals(option.isCorrect()))
                            .findFirst().orElse(null);
                    String answer = correct == null ? null : correct.textEn();
                    if (blank(answer) || occurrences(question.sentence().textEn(), answer) != 1) {
                        add(violations, path + ".sentence.textEn", "BLANK_COUNT", "Completed sentence must contain the correct word exactly once as its blank.");
                    }
                }
            }
        } else {
            count(violations, path + ".options", options.size(), 4, 4, "Situational-choice question must have exactly 4 options.");
            required(violations, path + ".prompt", question.prompt());
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i) == null || blank(options.get(i).textEn())) {
                    add(violations, path + ".options[" + i + "].textEn", "REQUIRED", "Answer option text is required.");
                }
            }
        }
    }

    private static int occurrences(String text, String value) {
        if (blank(text) || blank(value)) return 0;
        int count = 0;
        for (int from = 0, at; (at = text.indexOf(value, from)) >= 0; from = at + value.length()) count++;
        return count;
    }

    private static void required(List<Violation> violations, String path, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            add(violations, path, "REQUIRED", "This field is required for publish readiness.");
        }
    }

    private static void count(List<Violation> violations, String path, int value, int min, int max, String message) {
        if (value < min || value > max) add(violations, path, "COUNT_OUT_OF_RANGE", message);
    }

    private static void add(List<Violation> violations, String path, String code, String message) {
        violations.add(new Violation(path, code, message));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private void validateMedia(List<Violation> violations, Topic topic, String path, String value,
                               TopicMediaKind kind, boolean required) {
        if (blank(value)) {
            if (required) add(violations, path, "REQUIRED", "Required managed media is missing.");
            return;
        }
        if (!value.startsWith("topic-media:") || !media.isReference(topic, value, kind)) {
            add(violations, path, "INVALID_MEDIA", "Media must be a Topic-owned managed asset of the required kind.");
        }
    }

    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }

    private record QuestionRef(TopicManagementItemDetail.QuizQuestion question, String path) { }
}
