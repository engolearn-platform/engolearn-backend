package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.dictionary.contract.VocabularyLookup;
import fit.iuh.engolearn.topic.dto.request.TopicItemContextRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemVocabularyRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemExpressionsRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemQuizzesRequest;
import fit.iuh.engolearn.topic.dto.request.TopicItemMinorRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemResourceMutationResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicMinorRequiresMajorException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import fit.iuh.engolearn.topic.mapper.TopicManagementItemMapper;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.service.TopicItemDraftMutationGuard;
import fit.iuh.engolearn.topic.service.TopicManagementResourceService;
import fit.iuh.engolearn.topic.service.TopicMediaService;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
    private final TopicVersionRepository releases;
    private final TopicItemVersionRepository versions;
    private final ObjectMapper objectMapper;

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

    @Override
    @Transactional(transactionManager = "mongoTransactionManager")
    public TopicItemResourceMutationResponse minor(String topicId, String itemId, TopicItemMinorRequest request) {
        try {
            return minorTransaction(topicId, itemId, request);
        } catch (RuntimeException failure) {
            if (isConcurrentWrite(failure)) throw new TopicRevisionConflictException();
            throw failure;
        }
    }

    private TopicItemResourceMutationResponse minorTransaction(String topicId, String itemId, TopicItemMinorRequest request) {
        long expectedRevision = revision(request == null ? null : request.revision());
        if (request.itemVersionId() == null || request.itemVersionId().isBlank())
            throw invalid("itemVersionId", "The current published ItemVersion ID is required.");
        int supplied = (request.context() != null ? 1 : 0) + (request.vocabularies() != null ? 1 : 0)
                + (request.expressions() != null ? 1 : 0) + (request.quizzes() != null ? 1 : 0);
        if (supplied != 1) throw invalid("section", "Supply exactly one non-null resource section.");

        Topic topic = draftGuard.requireTopic(topicId);
        if (!Objects.equals(topic.getRevision() == null ? 0L : topic.getRevision(), expectedRevision))
            throw new TopicRevisionConflictException();
        String currentReleaseId = topic.getLatestPublishedVersion();
        TopicVersion release = currentReleaseId == null || currentReleaseId.isBlank() ? null : releases.findById(currentReleaseId).orElse(null);
        if (release == null || !Objects.equals(release.getTopicId(), topicId)
                || release.getStatus() != fit.iuh.engolearn.topic.model.enums.ContentStatus.PUBLISHED
                || release.getItemVersionIds() == null || !release.getItemVersionIds().contains(request.itemVersionId()))
            throw new TopicRevisionConflictException("The target ItemVersion is not in the current published release.");
        TopicItemVersion current = versions.findById(request.itemVersionId()).orElse(null);
        if (current == null || current.getStatus() != fit.iuh.engolearn.topic.model.enums.ContentStatus.PUBLISHED
                || !Objects.equals(current.getTopicId(), topicId) || !Objects.equals(current.getTopicItemId(), itemId))
            throw new TopicRevisionConflictException("The target ItemVersion is not the current published version of this Item.");
        if (versions.findAllByTopicIdAndTopicItemId(topicId, itemId).stream()
                .anyMatch(v -> v.getStatus() == fit.iuh.engolearn.topic.model.enums.ContentStatus.DRAFT))
            throw new TopicRevisionConflictException("A pending working copy exists; MINOR cannot overwrite it.");

        TopicManagementItemDetail before = mapper.detail(current);
        Object sectionBefore;
        Object sectionAfter;
        Set<String> allowed;
        String field;
        if (request.context() != null) {
            sectionBefore = before.context(); sectionAfter = request.context(); field = "context";
            allowed = Set.of("/dialogue/*/textVi", "/dialogue/*/audioUrl");
        } else if (request.vocabularies() != null) {
            sectionBefore = before.vocabularies(); sectionAfter = request.vocabularies(); field = "vocabularies";
            allowed = Set.of("/*/snapshot/sense", "/*/snapshot/describeVi", "/*/snapshot/phonetic",
                    "/*/snapshot/audioUrl", "/*/snapshot/audioEnUrl", "/*/snapshot/audioUsUrl",
                    "/*/samples/*/textVi", "/*/samples/*/audioUrl");
            if (stableVocabularyIdentities(before.vocabularies(), request.vocabularies())) {
                allowed = new HashSet<>(allowed);
                allowed.add("/*/snapshot/word");
            }
        } else if (request.expressions() != null) {
            sectionBefore = before.expressions(); sectionAfter = request.expressions(); field = "expressions";
            allowed = Set.of("/*/purposes/*/purposeVi", "/*/purposes/*/examples/*/textVi",
                    "/*/purposes/*/examples/*/usageNote", "/*/purposes/*/examples/*/audioUrl");
        } else {
            sectionBefore = before.quizzes(); sectionAfter = request.quizzes(); field = "quizzes";
            allowed = Set.of("/*/questions/*/explanation", "/*/questions/*/explanationOk", "/*/questions/*/explanationNg",
                    "/*/questions/*/audioUrl", "/*/questions/*/sentence/audioUrl", "/*/questions/*/sentence/audioUrlSlow");
        }
        JsonNode oldNode = objectMapper.valueToTree(sectionBefore);
        JsonNode newNode = objectMapper.valueToTree(sectionAfter);
        Map<String, JsonNode> leafChanges = new LinkedHashMap<>();
        if (!collectMinorChanges(oldNode, newNode, "", allowed, leafChanges))
            throw new TopicMinorRequiresMajorException("This change affects structure or learning semantics. Apply it through MAJOR.");
        validateChangedAudio(topic, oldNode, newNode);
        Topic updatedTopic = advancePublishedRevision(topicId, expectedRevision, currentReleaseId);
        Query query = new Query(new Criteria().andOperator(Criteria.where("_id").is(current.getId()),
                Criteria.where("topic_id").is(topicId), Criteria.where("topic_item_id").is(itemId),
                Criteria.where("status").is(fit.iuh.engolearn.topic.model.enums.ContentStatus.PUBLISHED),
                Criteria.where("topic_version_id").is(current.getTopicVersionId())));
        Update minorUpdate = new Update();
        leafChanges.forEach((path, leaf) -> minorUpdate.set(field + toMongoPath(path), objectMapper.convertValue(leaf, Object.class)));
        minorUpdate.set("updatedAt", Instant.now());
        if (mongo.updateFirst(query, minorUpdate, TopicItemVersion.class)
                .getModifiedCount() != 1) throw new TopicRevisionConflictException();
        return response(updatedTopic, mongo.findById(current.getId(), TopicItemVersion.class));
    }

    private static boolean collectMinorChanges(JsonNode before, JsonNode after, String path, Set<String> allowed,
                                               Map<String, JsonNode> changes) {
        if (before == null || after == null) return before == after;
        if (before.isObject() && after.isObject()) {
            if (before.size() != after.size()) return false;
            for (String name : before.propertyNames()) {
                if (!after.has(name) || !collectMinorChanges(before.get(name), after.get(name), path + "/" + name, allowed, changes)) return false;
            }
            return true;
        }
        if (before.isArray() && after.isArray()) {
            if (before.size() != after.size()) return false;
            for (int i = 0; i < before.size(); i++)
                if (!collectMinorChanges(before.get(i), after.get(i), path + "/" + i, allowed, changes)) return false;
            return true;
        }
        boolean mutable = allowed.stream().anyMatch(pattern -> pathMatches(path, pattern));
        if (before.equals(after)) return true;
        if (!mutable || after.isNull() || after.isTextual() && after.asText().isBlank()) return false;
        changes.put(path, after);
        return true;
    }

    private static String toMongoPath(String path) {
        StringBuilder result = new StringBuilder();
        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            result.append('.');
            if (part.chars().allMatch(Character::isDigit)) result.append(part);
            else result.append(part.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    private static boolean pathMatches(String path, String pattern) {
        String[] actual = path.split("/"); String[] expected = pattern.split("/");
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) if (!expected[i].equals("*") && !expected[i].equals(actual[i])) return false;
        return true;
    }

    private static boolean stableVocabularyIdentities(List<TopicManagementItemDetail.Vocabulary> before,
                                                        List<TopicManagementItemDetail.Vocabulary> after) {
        if (before == null || after == null || before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            var oldValue = before.get(i); var newValue = after.get(i);
            if (oldValue == null || newValue == null || oldValue.vocabId() == null || oldValue.vocabId().isBlank()
                    || oldValue.senseId() == null || oldValue.senseId().isBlank()
                    || !Objects.equals(oldValue.vocabId(), newValue.vocabId())
                    || !Objects.equals(oldValue.senseId(), newValue.senseId())) return false;
        }
        return true;
    }

    private void validateChangedAudio(Topic topic, JsonNode before, JsonNode after) {
        if (before.isObject() && after.isObject()) {
            for (String name : after.propertyNames()) {
                if (name.toLowerCase(Locale.ROOT).contains("audio") && !Objects.equals(before.get(name), after.get(name))) {
                    String replacement = after.get(name).asText();
                    if (replacement.isBlank() || !topicMedia.isReference(topic, replacement, fit.iuh.engolearn.topic.model.enums.TopicMediaKind.AUDIO))
                        throw invalid("media", "Changed audio must reference managed audio owned by this Topic.");
                }
                else validateChangedAudio(topic, before.get(name), after.get(name));
            }
        } else if (before.isArray() && after.isArray()) {
            for (int i = 0; i < Math.min(before.size(), after.size()); i++) validateChangedAudio(topic, before.get(i), after.get(i));
        }
    }

    private Topic advancePublishedRevision(String topicId, long expectedRevision, String releaseId) {
        Criteria revisionMatch = expectedRevision == 0
                ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").is(null))
                : Criteria.where("revision").is(expectedRevision);
        Query query = new Query(new Criteria().andOperator(Criteria.where("_id").is(topicId),
                Criteria.where("latestPublishedVersion").is(releaseId), revisionMatch));
        Topic updated = mongo.findAndModify(query, new Update().inc("revision", 1).set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Topic.class);
        if (updated == null) throw new TopicRevisionConflictException();
        return updated;
    }

    private static boolean isConcurrentWrite(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongoFailure
                    && (mongoFailure.getCode() == 112 || mongoFailure.getCode() == 251
                    || mongoFailure.hasErrorLabel("TransientTransactionError")
                    || mongoFailure.hasErrorLabel("UnknownTransactionCommitResult"))) return true;
        }
        return false;
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
