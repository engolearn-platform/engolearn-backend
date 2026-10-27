package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.*;
import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicAttemptContentMapper;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.repository.*;
import com.mongodb.client.model.*;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service
public class TopicAttemptService {
    private final LocalLearnerProvider learner;
    private final TopicLearnerCatalogService catalog;
    private final UserTopicItemAttemptRepository attempts;
    private final TopicItemVersionRepository versions;
    private final MongoTemplate mongo;
    private final TopicItemRepository topicItems;
    private final TopicLearningService learning;

    public TopicAttemptService(LocalLearnerProvider learner, TopicLearnerCatalogService catalog,
            UserTopicItemAttemptRepository attempts, TopicItemVersionRepository versions, MongoTemplate mongo,
            TopicItemRepository topicItems, TopicLearningService learning) {
        this.learner = learner; this.catalog = catalog; this.attempts = attempts; this.versions = versions; this.mongo = mongo;
        this.topicItems = topicItems; this.learning = learning;
    }

    @PostConstruct
    void indexes() {
        mongo.execute(UserTopicItemAttempt.class, collection -> collection.createIndex(
                Indexes.compoundIndex(Indexes.ascending("user_id"), Indexes.ascending("topic_id"), Indexes.ascending("attempt_type")),
                new IndexOptions().name("one_running_learning_item_per_topic").unique(true).partialFilterExpression(
                        Filters.and(Filters.eq("status", "IN_PROGRESS"), Filters.eq("attempt_type", "LEARNING")))));
    }

    public TopicAttemptResponse start(String itemId, StartTopicAttemptRequest body, String key) {
        String userId = userId();
        if (!"LEARN".equals(body.mode())) throw problem(501, "CONTENT_UNAVAILABLE", "Retry is not implemented in this slice");
        if (body.retrySessionId() != null || body.replaceRetrySessionId() != null) throw problem(400, "INVALID_REQUEST", "LEARN cannot attach a retry session");
        String signature = "start|" + itemId + "|" + body.mode();
        String id = hash(userId + "|" + key).substring(0, 24);
        UserTopicItemAttempt previous = attempts.findById(id).orElse(null);
        if (previous != null) {
            if (!userId.equals(previous.getUserId()) || !signature.equals(previous.getStartFingerprint())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another action");
            return describe(previous, LearningStage.CONTEXT, 0, 0, previous.getStartedAt());
        }
        TopicItem storedItem = topicItems.findById(itemId).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Topic item not found"));
        TopicDetailResponse detail = catalog.detail(storedItem.getTopicId());
        TopicItemSummaryResponse item = detail.getItems().stream().filter(row -> row.getTopicItemId().equals(itemId)).findFirst()
                .orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Topic item is not published"));
        if (item.getLearningAttempt() != null) return describe(owned(item.getLearningAttempt().attemptId()));
        if (!item.getActions().start().allowed()) throw problem(422, item.getActions().start().reason(), "The item cannot be started");
        TopicItemVersion pinned = versions.findById(item.getPublishedVersionId()).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Published version not found"));
        TopicAttemptContentMapper.content(pinned);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        UserTopicItemAttempt created = UserTopicItemAttempt.builder().id(id).userId(userId).topicId(detail.getTopicId()).topicItemId(itemId)
                .topicItemVersionId(pinned.getId()).attemptType(AttemptType.LEARNING).startRequestKey(key).startFingerprint(signature)
                .mutationReceipts(new ArrayList<>()).status(AttemptStatus.IN_PROGRESS)
                .furthestReached(FurthestReachedVo.builder().stage(LearningStage.CONTEXT).itemIndex(0).build())
                .startedAt(now).lastAccessedAt(now).build();
        try { return describe(attempts.insert(created)); }
        catch (DuplicateKeyException conflict) {
            UserTopicItemAttempt keyed = attempts.findById(id).orElse(null);
            if (keyed != null && !signature.equals(keyed.getStartFingerprint())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another action");
            return attempts.findByUserIdAndTopicId(userId, detail.getTopicId()).stream()
                    .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS && a.getAttemptType() == AttemptType.LEARNING && itemId.equals(a.getTopicItemId()))
                    .findFirst().map(this::describe).orElseThrow(() -> problem(409, "OTHER_ITEM_IN_PROGRESS", "Another item was started"));
        }
    }

    public TopicAttemptResponse get(String id) { return describe(owned(id)); }

    public TopicItemContentResponse content(String id) {
        return TopicAttemptContentMapper.content(version(owned(id)));
    }

    public TopicAttemptResponse advance(String id, AdvanceTopicContentRequest body, String key) {
        UserTopicItemAttempt attempt = owned(id);
        String fingerprint = "advance|" + body.revision() + "|" + body.stage() + "|" + body.itemIndex();
        ContentMutationReceiptVo replay = receipt(attempt, key, fingerprint);
        if (replay != null) return describe(attempt, replay.stage(), replay.itemIndex(), replay.revision(), replay.accessedAt());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS || revision(attempt) != body.revision() ||
                attempt.getFurthestReached().getStage() != body.stage() || !Objects.equals(attempt.getFurthestReached().getItemIndex(), body.itemIndex())) {
            throw problem(409, "STATE_CHANGED", "Reload the saved position before continuing");
        }
        TopicItemContentResponse content = TopicAttemptContentMapper.content(version(attempt));
        List<TopicCatalogPolicy.Position> positions = contentPositions(content);
        int index = positions.indexOf(new TopicCatalogPolicy.Position(body.stage().name(), body.itemIndex()));
        if (index < 0) throw problem(409, "INVALID_STAGE", "Only the current content unit can be advanced");
        TopicCatalogPolicy.Position next;
        if (index + 1 < positions.size()) next = positions.get(index + 1);
        else {
            TopicItemVersion version = version(attempt);
            LearningStage quizStage = quizCount(version, QuizType.FILL_BLANK) > 0 ? LearningStage.FILL_BLANK :
                    quizCount(version, QuizType.SITUATIONAL_CHOICE) > 0 ? LearningStage.SITUATIONAL_CHOICE : null;
            if (quizStage == null) throw problem(422, "CONTENT_UNAVAILABLE", "This item has no published supported exercise");
            next = new TopicCatalogPolicy.Position(quizStage.name(), 0);
        }
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        long revision = revision(attempt) + 1;
        ContentMutationReceiptVo saved = new ContentMutationReceiptVo(key, fingerprint, LearningStage.valueOf(next.stage()), next.itemIndex(), revision, now);
        Query query = cas(attempt);
        Update update = new Update().set("furthest_reached", FurthestReachedVo.builder().stage(saved.stage()).itemIndex(saved.itemIndex()).build())
                .set("version", revision).set("last_accessed_at", now).push("mutation_receipts", saved);
        UserTopicItemAttempt result = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
        if (result == null) {
            UserTopicItemAttempt latest = owned(id);
            ContentMutationReceiptVo concurrentReplay = receipt(latest, key, fingerprint);
            if (concurrentReplay != null) return describe(latest, concurrentReplay.stage(), concurrentReplay.itemIndex(), concurrentReplay.revision(), concurrentReplay.accessedAt());
            throw problem(409, "STATE_CHANGED", "Another request changed the position");
        }
        return describe(result);
    }

    public Map<String, Instant> access(String id, String key) {
        UserTopicItemAttempt attempt = owned(id);
        ContentMutationReceiptVo replay = receipt(attempt, key, "access");
        if (replay != null) return Map.of("lastAccessedAt", replay.accessedAt());
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        ContentMutationReceiptVo saved = new ContentMutationReceiptVo(key, "access", null, 0, revision(attempt), now);
        UserTopicItemAttempt result = mongo.findAndModify(cas(attempt),
                new Update().set("version", attempt.getVersion()).set("last_accessed_at", now).push("mutation_receipts", saved),
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
        if (result == null) throw problem(409, "STATE_CHANGED", "Attempt changed while recording access");
        return Map.of("lastAccessedAt", now);
    }

    public TopicAttemptResponse describe(UserTopicItemAttempt attempt) {
        FurthestReachedVo position = attempt.getFurthestReached();
        return describe(attempt, position == null ? null : position.getStage(), position == null ? 0 : position.getItemIndex(), revision(attempt), attempt.getLastAccessedAt());
    }

    private TopicAttemptResponse describe(UserTopicItemAttempt attempt, LearningStage stage, int index, long revision, Instant accessed) {
        TopicItemVersion version = version(attempt);
        TopicItemContentResponse content = TopicAttemptContentMapper.content(version);
        List<TopicCatalogPolicy.Position> positions = contentPositions(content);
        int totalQuiz = quizCount(version, QuizType.FILL_BLANK) + quizCount(version, QuizType.SITUATIONAL_CHOICE);
        int completed = stage == null ? positions.size() + totalQuiz : positions.indexOf(new TopicCatalogPolicy.Position(stage.name(), index));
        if (completed < 0) completed = positions.size() + (stage == LearningStage.SITUATIONAL_CHOICE ? quizCount(version, QuizType.FILL_BLANK) : 0) + index;
        int learnedWords = 0, learnedExpressions = 0;
        for (int i = 0; i < Math.min(completed, positions.size()); i++) {
            TopicCatalogPolicy.Position position = positions.get(i);
            if ("VOCAB".equals(position.stage())) learnedWords++;
            if ("EXPRESSIONS".equals(position.stage())) learnedExpressions += purposes(content).get(position.itemIndex()).getExamples().size();
        }
        int total = positions.size() + totalQuiz;
        int doneQuiz = Math.max(0, completed - positions.size());
        TopicAttemptResponse.Progress progress = new TopicAttemptResponse.Progress(completed, total, total == 0 ? 0 : (int)(100L * completed / total),
                learnedWords, learnedExpressions, doneQuiz, totalQuiz);
        return new TopicAttemptResponse(attempt.getId(), attempt.getTopicId(), attempt.getTopicItemId(), attempt.getTopicItemVersionId(),
                version.getVersion(), "LEARN", stage == null ? attempt.getStatus().name() : "IN_PROGRESS", revision,
                stage == null ? null : new TopicCatalogPolicy.Position(stage.name(), index), progress, null, attempt.getStartedAt(), accessed, attempt.getCompletedAt());
    }

    public TopicProgressResponse topicProgress(String topicId) {
        TopicProgressResponse response = learning.getTopicProgress(GetTopicProgressRequest.builder().topicId(topicId).build());
        TopicDetailResponse detail = catalog.detail(topicId);
        List<UserTopicItemAttempt> saved = attempts.findByUserIdAndTopicId(userId(), topicId);
        int totalWords = 0, totalExpressions = 0, totalQuiz = 0, learnedWords = 0, learnedExpressions = 0, doneQuiz = 0;
        Instant accessed = response.getLastAccessedAt();
        for (TopicItemSummaryResponse item : detail.getItems()) {
            UserTopicItemAttempt active = saved.stream().filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS &&
                    a.getAttemptType() == AttemptType.LEARNING && item.getTopicItemId().equals(a.getTopicItemId())).findFirst().orElse(null);
            TopicItemVersion pinned = versions.findById(active == null ? item.getPublishedVersionId() : active.getTopicItemVersionId()).orElseThrow(
                    () -> problem(404, "CONTENT_UNAVAILABLE", "Content version not found"));
            TopicItemContentResponse content = TopicAttemptContentMapper.content(pinned);
            int words = content.getVocabularies().size();
            int expressions = purposes(content).stream().mapToInt(p -> p.getExamples().size()).sum();
            int quizzes = quizCount(pinned, QuizType.FILL_BLANK) + quizCount(pinned, QuizType.SITUATIONAL_CHOICE);
            totalWords += words; totalExpressions += expressions; totalQuiz += quizzes;
            if ("COMPLETED".equals(item.getLearningStatus())) {
                learnedWords += words; learnedExpressions += expressions; doneQuiz += quizzes;
            } else if (active != null) {
                TopicAttemptResponse.Progress progress = describe(active).progress();
                learnedWords += progress.learnedVocabularyCount(); learnedExpressions += progress.learnedExpressionCount(); doneQuiz += progress.completedQuizCount();
            }
            if (active != null && active.getLastAccessedAt() != null && (accessed == null || active.getLastAccessedAt().isAfter(accessed))) accessed = active.getLastAccessedAt();
        }
        response.setPercent(response.getTotalItemCount() == 0 ? 0 : (int)Math.round(100.0 * response.getCompletedItemCount() / response.getTotalItemCount()));
        response.setHasNewContent(detail.isHasNewContent());
        response.setLastAccessedAt(accessed);
        response.setMetrics(new TopicProgressResponse.Metrics(learnedWords, totalWords, learnedExpressions, totalExpressions, doneQuiz, totalQuiz));
        return response;
    }

    private List<TopicCatalogPolicy.Position> contentPositions(TopicItemContentResponse content) {
        List<TopicCatalogPolicy.Position> positions = new ArrayList<>();
        positions.add(new TopicCatalogPolicy.Position("CONTEXT", 0));
        for (int i = 0; i < content.getVocabularies().size(); i++) positions.add(new TopicCatalogPolicy.Position("VOCAB", i));
        for (int i = 0; i < purposes(content).size(); i++) positions.add(new TopicCatalogPolicy.Position("EXPRESSIONS", i));
        return positions;
    }

    private List<TopicExpressionPurposeResponse> purposes(TopicItemContentResponse content) {
        return content.getExpressions().stream().flatMap(group -> group.getPurposes().stream()).toList();
    }

    public int quizCount(TopicItemVersion version, QuizType type) {
        return version.getQuizzes() == null ? 0 : version.getQuizzes().stream().filter(module -> module.getQuizType() == type)
                .mapToInt(module -> module.getQuestions() == null ? 0 : module.getQuestions().size()).sum();
    }

    private Query cas(UserTopicItemAttempt attempt) {
        return new Query(Criteria.where("_id").is(attempt.getId()).and("user_id").is(attempt.getUserId()).and("version").is(attempt.getVersion()));
    }

    private ContentMutationReceiptVo receipt(UserTopicItemAttempt attempt, String key, String fingerprint) {
        if (key.equals(attempt.getStartRequestKey())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for start");
        if (attempt.getMutationReceipts() != null) for (ContentMutationReceiptVo receipt : attempt.getMutationReceipts()) {
            if (receipt.key().equals(key)) {
                if (!receipt.fingerprint().equals(fingerprint)) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another action");
                return receipt;
            }
        }
        return null;
    }

    private UserTopicItemAttempt owned(String id) {
        String owner = userId();
        UserTopicItemAttempt attempt = attempts.findById(id).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Attempt not found"));
        if (!owner.equals(attempt.getUserId())) throw problem(404, "CONTENT_UNAVAILABLE", "Attempt not found");
        return attempt;
    }

    private TopicItemVersion version(UserTopicItemAttempt attempt) {
        return versions.findById(attempt.getTopicItemVersionId()).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Saved content version not found"));
    }

    private String userId() {
        String id = learner.getCurrentUserId();
        if (id == null) throw problem(401, "SIGN_IN_REQUIRED", "Select a local learner profile");
        return id;
    }
    private long revision(UserTopicItemAttempt attempt) { return attempt.getVersion() == null ? 0 : attempt.getVersion(); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private TopicRequestException problem(int status, String code, String detail) { return new TopicRequestException(status, code, detail); }
}
