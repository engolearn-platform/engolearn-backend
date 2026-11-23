package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.dictionary.contract.VocabularyLookup;
import fit.iuh.engolearn.topic.dto.request.TopicItemContextRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemVocabularyRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemExpressionsRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemQuizzesRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemResourceMutationResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.mapper.TopicManagementItemMapper;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.service.TopicItemDraftMutationGuard;
import fit.iuh.engolearn.topic.service.TopicManagementResourceService;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
@Transactional
public class TopicManagementResourceServiceImpl implements TopicManagementResourceService {
    private final TopicItemDraftMutationGuard draftGuard;
    private final MongoTemplate mongo;
    private final TopicManagementItemMapper mapper;
    private final VocabularyLookup vocabularyLookup;
    private final TopicMediaService topicMedia;

    @Override
    public TopicItemResourceMutationResponse context(String topicId, String itemId, TopicItemContextRequest request) {
        long expectedRevision = revision(request == null ? null : request.revision());
        Topic topic = draftGuard.requireTopic(topicId);
        TopicItemVersion draft = draftGuard.requireWritableDraft(topicId, itemId);
        validateContext(topic, request.context());
        TopicContextVo context = toContext(request.context());
        Topic updatedTopic = draftGuard.advanceRevision(topicId, expectedRevision);
        update(draft, "context", context);
        TopicItemVersion updated = mongo.findById(draft.getId(), TopicItemVersion.class);
        return response(updatedTopic, updated);
    }

    @Override
    public TopicItemResourceMutationResponse vocabulary(String topicId, String itemId, TopicItemVocabularyRequest request) {
        long expectedRevision = revision(request == null ? null : request.revision());
        if (request.vocabularies() == null) throw invalid("vocabularies", "vocabularies is required.");
        Topic topic = draftGuard.requireTopic(topicId);
        TopicItemVersion draft = draftGuard.requireWritableDraft(topicId, itemId);
        List<TopicManagementItemDetail.Vocabulary> incoming = request.vocabularies();
        if (incoming.stream().anyMatch(Objects::isNull)) throw invalid("vocabularies", "Entries must not be null.");
        validateVocabularies(incoming);
        validateVocabularyAudio(topic, incoming);
        validateVocabularyRefs(draft.getVocabularies(), incoming);
        preventRemovingLinkedVocabularies(draft, incoming);
        Topic updatedTopic = draftGuard.advanceRevision(topicId, expectedRevision);
        List<ItemWordVo> vocabularies = incoming.stream().map(this::toVocabulary).toList();
        update(draft, "vocabularies", vocabularies);
        TopicItemVersion updated = mongo.findById(draft.getId(), TopicItemVersion.class);
        return response(updatedTopic, updated);
    }

    @Override
    public TopicItemResourceMutationResponse expressions(String topicId, String itemId, TopicItemExpressionsRequest request) {
        long expectedRevision = revision(request == null ? null : request.revision());
        if (request.expressions() == null) throw invalid("expressions", "expressions is required.");
        Topic topic = draftGuard.requireTopic(topicId);
        TopicItemVersion draft = draftGuard.requireWritableDraft(topicId, itemId);
        List<TopicManagementItemDetail.ExpressionBlock> incoming = request.expressions();
        if (incoming.stream().anyMatch(Objects::isNull)) throw invalid("expressions", "Blocks must not be null.");
        validateExpressions(draft, incoming);
        validateExpressionAudio(topic, incoming);
        Topic updatedTopic = draftGuard.advanceRevision(topicId, expectedRevision);
        List<ExpressionBlockVo> expressions = incoming.stream().map(TopicManagementResourceServiceImpl::toExpressionBlock).toList();
        update(draft, "expressions", expressions);
        return response(updatedTopic, mongo.findById(draft.getId(), TopicItemVersion.class));
    }

    @Override
    public TopicItemResourceMutationResponse quizzes(String topicId, String itemId, TopicItemQuizzesRequest request) {
        long expectedRevision = revision(request == null ? null : request.revision());
        if (request.quizzes() == null) throw invalid("quizzes", "quizzes is required.");
        Topic topic = draftGuard.requireTopic(topicId);
        TopicItemVersion draft = draftGuard.requireWritableDraft(topicId, itemId);
        List<TopicManagementItemDetail.QuizModule> incoming = request.quizzes();
        if (incoming.stream().anyMatch(Objects::isNull)) throw invalid("quizzes", "Modules must not be null.");
        validateQuizzes(incoming);
        validateQuizAudio(topic, incoming);
        Topic updatedTopic = draftGuard.advanceRevision(topicId, expectedRevision);
        List<QuizModuleVo> quizzes = incoming.stream().map(TopicManagementResourceServiceImpl::toQuizModule).toList();
        update(draft, "quizzes", quizzes);
        return response(updatedTopic, mongo.findById(draft.getId(), TopicItemVersion.class));
    }

    private static void validateExpressions(TopicItemVersion draft,
            List<TopicManagementItemDetail.ExpressionBlock> incoming) {
        Set<String> vocabularyIds = Optional.ofNullable(draft.getVocabularies()).orElse(List.of()).stream()
                .filter(Objects::nonNull).map(ItemWordVo::getVocabId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> unchangedLinks = Optional.ofNullable(draft.getExpressions()).orElse(List.of()).stream()
                .filter(Objects::nonNull).flatMap(block -> Optional.ofNullable(block.getPurposes()).orElse(List.of()).stream())
                .filter(Objects::nonNull).flatMap(purpose -> Optional.ofNullable(purpose.getExamples()).orElse(List.of()).stream())
                .filter(Objects::nonNull).flatMap(example -> Optional.ofNullable(example.getLinkedVocabIds()).orElse(List.of()).stream())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (var block : incoming) {
            if (block.purposes() != null && block.purposes().stream().anyMatch(Objects::isNull)) {
                throw invalid("expressions.purposes", "Purpose entries must not be null.");
            }
            for (var purpose : Optional.ofNullable(block.purposes()).orElse(List.of())) {
                if (purpose.examples() != null && purpose.examples().stream().anyMatch(Objects::isNull)) {
                    throw invalid("expressions.examples", "Expression entries must not be null.");
                }
                for (var example : Optional.ofNullable(purpose.examples()).orElse(List.of())) {
                    for (String linkedId : Optional.ofNullable(example.linkedVocabIds()).orElse(List.of())) {
                        if (linkedId == null || linkedId.isBlank()) throw invalid("expressions.linkedVocabIds", "Vocabulary references must not be blank.");
                        if (!vocabularyIds.contains(linkedId) && !unchangedLinks.contains(linkedId)) {
                            throw invalid("expressions.linkedVocabIds", "Linked vocabulary must belong to this item.");
                        }
                    }
                }
            }
        }
    }

    private static void validateQuizzes(List<TopicManagementItemDetail.QuizModule> modules) {
        for (var module : modules) {
            if (module.quizType() != QuizType.FILL_BLANK && module.quizType() != QuizType.SITUATIONAL_CHOICE) {
                throw invalid("quizzes.quizType", "Only FILL_BLANK and SITUATIONAL_CHOICE are supported.");
            }
            if (module.questions() != null && module.questions().stream().anyMatch(Objects::isNull)) {
                throw invalid("quizzes.questions", "Question entries must not be null.");
            }
            for (var question : Optional.ofNullable(module.questions()).orElse(List.of())) {
                List<TopicManagementItemDetail.QuizOption> options = Optional.ofNullable(question.options()).orElse(List.of());
                Set<String> labels = new HashSet<>();
                int correctCount = 0;
                String markedCorrectLabel = null;
                for (var option : options) {
                    if (option == null) throw invalid("quizzes.options", "Option entries must not be null.");
                    if (option.label() != null && !labels.add(option.label().toLowerCase(Locale.ROOT))) {
                        throw invalid("quizzes.options.label", "Option labels must be distinct.");
                    }
                    if (Boolean.TRUE.equals(option.isCorrect())) {
                        correctCount++;
                        markedCorrectLabel = option.label();
                    }
                }
                if (correctCount > 1) throw invalid("quizzes.options.isCorrect", "At most one option may be marked correct.");
                if (question.correct() != null && !question.correct().isBlank()) {
                    String answer = question.correct().trim();
                    boolean matches = options.stream().anyMatch(option -> answer.equals(option.label()));
                    if (!matches) throw invalid("quizzes.correct", "Correct answer must match a supplied option label or text.");
                    if (correctCount == 1 && !answer.equals(markedCorrectLabel)) {
                        throw invalid("quizzes.correct", "Correct answer must match the option marked correct.");
                    }
                }
                if (module.quizType() == QuizType.FILL_BLANK && completeFillBlank(question, options)) {
                    if (options.size() < 4 || options.size() > 6 || correctCount != 1) {
                        throw invalid("quizzes", "A complete FILL_BLANK question needs 4–6 options and one correct answer.");
                    }
                    String answerText = options.stream().filter(option -> Boolean.TRUE.equals(option.isCorrect()))
                            .map(TopicManagementItemDetail.QuizOption::textEn).findFirst().orElse("");
                    String sentence = question.sentence().textEn();
                    if (answerText.isBlank() || occurrences(sentence, answerText) != 1) {
                        throw invalid("quizzes.sentence.textEn", "Completed sentence must contain the correct answer exactly once.");
                    }
                }
                if (module.quizType() == QuizType.SITUATIONAL_CHOICE && completeSituationalChoice(question, options)) {
                    if (options.size() != 4 || correctCount != 1) {
                        throw invalid("quizzes", "A complete SITUATIONAL_CHOICE question needs four options and one correct answer.");
                    }
                }
            }
        }
    }

    private static boolean completeFillBlank(TopicManagementItemDetail.QuizQuestion question,
                                               List<TopicManagementItemDetail.QuizOption> options) {
        return !options.isEmpty() && question.sentence() != null && question.sentence().textEn() != null
                && !question.sentence().textEn().isBlank();
    }

    private static boolean completeSituationalChoice(TopicManagementItemDetail.QuizQuestion question,
                                                       List<TopicManagementItemDetail.QuizOption> options) {
        return !options.isEmpty() && (question.correct() != null && !question.correct().isBlank()
                || options.stream().anyMatch(option -> option.isCorrect() != null));
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        for (int at = text.indexOf(value); at >= 0; at = text.indexOf(value, at + value.length()) ) count++;
        return count;
    }

    private static ExpressionBlockVo toExpressionBlock(TopicManagementItemDetail.ExpressionBlock value) {
        return ExpressionBlockVo.builder().tips(value.tips()).purposes(value.purposes() == null ? null : value.purposes().stream()
                .map(purpose -> ExpressionPurposeVo.builder().purposeEn(purpose.purposeEn()).purposeVi(purpose.purposeVi())
                        .orderIndex(purpose.orderIndex()).groupType(purpose.groupType())
                        .examples(purpose.examples() == null ? null : purpose.examples().stream().map(example -> ExpressionExampleVo.builder()
                                .type(example.type()).label(example.label()).textEn(example.textEn()).textVi(example.textVi())
                                .usageNote(example.usageNote()).audioUrl(example.audioUrl()).linkedVocabIds(example.linkedVocabIds()).build()).toList()).build()).toList()).build();
    }

    private static QuizModuleVo toQuizModule(TopicManagementItemDetail.QuizModule module) {
        return QuizModuleVo.builder().quizId(module.quizId()).quizType(module.quizType()).title(module.title())
                .instructions(module.instructions()).orderIndex(module.orderIndex())
                .questions(module.questions() == null ? null : module.questions().stream().map(question -> QuizQuestionVo.builder()
                        .prompt(question.prompt()).audioUrl(question.audioUrl()).correct(question.correct())
                        .explanationOk(question.explanationOk()).explanationNg(question.explanationNg())
                        .sentence(question.sentence() == null ? null : QuizSentenceVo.builder().textEn(question.sentence().textEn())
                                .meaningVi(question.sentence().meaningVi()).audioUrl(question.sentence().audioUrl())
                                .audioUrlSlow(question.sentence().audioUrlSlow()).source(question.sentence().source())
                                .externalId(question.sentence().externalId()).build())
                        .options(question.options() == null ? null : question.options().stream().map(option -> QuizOptionVo.builder()
                                .label(option.label()).textEn(option.textEn()).isCorrect(option.isCorrect()).build()).toList())
                        .quizData(question.quizData()).answerData(question.answerData()).explanation(question.explanation())
                        .tips(question.tips()).build()).toList()).build();
    }

    private void validateVocabularyRefs(List<ItemWordVo> current, List<TopicManagementItemDetail.Vocabulary> incoming) {
        Set<String> unchanged = current == null ? Set.of() : current.stream().map(TopicManagementResourceServiceImpl::ref)
                .collect(Collectors.toSet());
        for (var vocabulary : incoming) {
            String vocabId = vocabulary.vocabId();
            String senseId = vocabulary.senseId();
            String key = vocabId + "\u0000" + senseId;
            boolean hasVocabId = vocabId != null && !vocabId.isBlank();
            boolean hasSenseId = senseId != null && !senseId.isBlank();
            if (hasVocabId != hasSenseId && !unchanged.contains(key)) {
                throw invalid("vocabularies", "vocabId and senseId must be supplied together.");
            }
            if (!hasVocabId) continue;
            if (!unchanged.contains(key) && vocabularyLookup.findSense(vocabId, senseId).isEmpty()) {
                throw invalid("vocabularies", "Vocabulary and sense must reference an existing dictionary entry.");
            }
        }
    }

    private static String ref(ItemWordVo vocabulary) {
        return vocabulary.getVocabId() + "\u0000" + vocabulary.getSenseId();
    }

    private void preventRemovingLinkedVocabularies(TopicItemVersion current,
                                                     List<TopicManagementItemDetail.Vocabulary> incoming) {
        Set<String> retained = incoming.stream().map(TopicManagementItemDetail.Vocabulary::vocabId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> linked = Optional.ofNullable(current.getExpressions()).orElse(List.of()).stream()
                .filter(Objects::nonNull).flatMap(block -> Optional.ofNullable(block.getPurposes()).orElse(List.of()).stream())
                .filter(Objects::nonNull).flatMap(purpose -> Optional.ofNullable(purpose.getExamples()).orElse(List.of()).stream())
                .filter(Objects::nonNull).flatMap(example -> Optional.ofNullable(example.getLinkedVocabIds()).orElse(List.of()).stream())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (linked.stream().anyMatch(id -> current.getVocabularies() != null
                && current.getVocabularies().stream().filter(Objects::nonNull)
                .anyMatch(vocabulary -> Objects.equals(vocabulary.getVocabId(), id)) && !retained.contains(id))) {
            throw invalid("vocabularies", "A vocabulary linked from an expression cannot be removed.");
        }
    }

    private void update(TopicItemVersion draft, String field, Object value) {
        Query query = new Query(new Criteria().andOperator(Criteria.where("_id").is(draft.getId()),
                Criteria.where("topic_id").is(draft.getTopicId()), Criteria.where("topic_item_id").is(draft.getTopicItemId()),
                Criteria.where("status").is(draft.getStatus())));
        if (mongo.updateFirst(query, new Update().set(field, value).set("updatedAt", Instant.now()), TopicItemVersion.class)
                .getModifiedCount() != 1) throw new fit.iuh.engolearn.topic.exception.TopicRevisionConflictException();
    }

    private TopicItemResourceMutationResponse response(Topic topic, TopicItemVersion version) {
        return new TopicItemResourceMutationResponse(topic.getRevision() == null ? 0 : topic.getRevision(), mapper.detail(version));
    }

    private static TopicContextVo toContext(TopicManagementItemDetail.Context value) {
        if (value == null) return null;
        return TopicContextVo.builder().prompt(value.prompt()).scenarioDescription(value.scenarioDescription())
                .dialogue(value.dialogue() == null ? null : value.dialogue().stream().map(turn -> DialogueTurnVo.builder()
                        .speaker(turn.speaker()).textEn(turn.textEn()).textVi(turn.textVi()).audioUrl(turn.audioUrl()).build()).toList())
                .durationSec(value.durationSec()).tips(value.tips()).build();
    }

    private void validateContext(Topic topic, TopicManagementItemDetail.Context context) {
        if (context == null) return;
        if (context.durationSec() != null && context.durationSec() < 0) {
            throw invalid("context.durationSec", "durationSec must not be negative.");
        }
        if (context.dialogue() != null && context.dialogue().stream().anyMatch(Objects::isNull)) {
            throw invalid("context.dialogue", "Dialogue entries must not be null.");
        }
        Optional.ofNullable(context.dialogue()).orElse(List.of()).forEach(turn -> topicMedia.validateAudioReference(topic, turn.audioUrl()));
    }

    private void validateVocabularyAudio(Topic topic, List<TopicManagementItemDetail.Vocabulary> values) {
        for (var value : values) {
            if (value.snapshot() != null) {
                topicMedia.validateAudioReference(topic, value.snapshot().audioUrl());
                topicMedia.validateAudioReference(topic, value.snapshot().audioEnUrl());
                topicMedia.validateAudioReference(topic, value.snapshot().audioUsUrl());
            }
            Optional.ofNullable(value.samples()).orElse(List.of()).forEach(sample -> topicMedia.validateAudioReference(topic, sample.audioUrl()));
            if (value.quickCheck() != null && value.quickCheck().sentence() != null) {
                topicMedia.validateAudioReference(topic, value.quickCheck().sentence().audioUrl());
                topicMedia.validateAudioReference(topic, value.quickCheck().sentence().audioUrlSlow());
            }
        }
    }

    private void validateExpressionAudio(Topic topic, List<TopicManagementItemDetail.ExpressionBlock> values) {
        Optional.ofNullable(values).orElse(List.of()).forEach(block -> Optional.ofNullable(block.purposes()).orElse(List.of())
                .forEach(purpose -> Optional.ofNullable(purpose.examples()).orElse(List.of())
                        .forEach(example -> topicMedia.validateAudioReference(topic, example.audioUrl()))));
    }

    private void validateQuizAudio(Topic topic, List<TopicManagementItemDetail.QuizModule> values) {
        Optional.ofNullable(values).orElse(List.of()).forEach(module -> Optional.ofNullable(module.questions()).orElse(List.of())
                .forEach(question -> {
                    topicMedia.validateAudioReference(topic, question.audioUrl());
                    if (question.sentence() != null) {
                        topicMedia.validateAudioReference(topic, question.sentence().audioUrl());
                        topicMedia.validateAudioReference(topic, question.sentence().audioUrlSlow());
                    }
                }));
    }

    private static void validateVocabularies(List<TopicManagementItemDetail.Vocabulary> values) {
        for (var vocabulary : values) {
            if (vocabulary.samples() != null && vocabulary.samples().stream().anyMatch(Objects::isNull)) {
                throw invalid("vocabularies.samples", "Sample entries must not be null.");
            }
            if (vocabulary.collocations() != null && vocabulary.collocations().stream().anyMatch(Objects::isNull)) {
                throw invalid("vocabularies.collocations", "Collocation entries must not be null.");
            }
        }
    }

    private ItemWordVo toVocabulary(TopicManagementItemDetail.Vocabulary value) {
        return ItemWordVo.builder().vocabId(value.vocabId()).senseId(value.senseId()).snapshot(toSnapshot(value.snapshot()))
                .samples(value.samples() == null ? null : value.samples().stream().map(sample -> WordSampleVo.builder()
                        .textEn(sample.textEn()).textVi(sample.textVi()).audioUrl(sample.audioUrl()).isManual(sample.isManual()).build()).toList())
                .collocations(value.collocations() == null ? null : value.collocations().stream().map(this::toCollocation).toList())
                .quickCheck(toQuickCheck(value.quickCheck())).orderIndex(value.orderIndex()).tips(value.tips()).build();
    }

    private static WordSnapshotVo toSnapshot(TopicManagementItemDetail.WordSnapshot value) {
        if (value == null) return null;
        return WordSnapshotVo.builder().word(value.word()).pos(value.pos()).sense(value.sense()).describeVi(value.describeVi())
                .phonetic(value.phonetic()).audioUrl(value.audioUrl()).audioEnUrl(value.audioEnUrl()).audioUsUrl(value.audioUsUrl()).build();
    }

    private CollocationVo toCollocation(TopicManagementItemDetail.Collocation value) {
        var example = value.example() == null ? null : CollocationExampleVo.builder().en(value.example().en()).vi(value.example().vi()).build();
        return CollocationVo.builder().text(value.text()).example(example).descriptionVi(value.descriptionVi()).build();
    }

    private static QuickCheckVo toQuickCheck(TopicManagementItemDetail.QuickCheck value) {
        if (value == null) return null;
        var sentence = value.sentence() == null ? null : QuizSentenceVo.builder().textEn(value.sentence().textEn())
                .meaningVi(value.sentence().meaningVi()).audioUrl(value.sentence().audioUrl())
                .audioUrlSlow(value.sentence().audioUrlSlow()).source(value.sentence().source()).externalId(value.sentence().externalId()).build();
        return QuickCheckVo.builder().sentence(sentence).quizData(value.quizData()).answerData(value.answerData()).explanation(value.explanation()).build();
    }

    private static long revision(BigDecimal revision) {
        if (revision == null) throw invalid("revision", "revision must be a non-negative integer.");
        try {
            long value = revision.longValueExact();
            if (value < 0) throw invalid("revision", "revision must be a non-negative integer.");
            return value;
        } catch (ArithmeticException exception) {
            throw invalid("revision", "revision must be a non-negative integer.");
        }
    }

    private static TopicMutationValidationException invalid(String field, String message) {
        return new TopicMutationValidationException(field, message);
    }
}
