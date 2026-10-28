package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.*;
import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicAttemptContentMapper;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
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
    @org.springframework.beans.factory.annotation.Autowired
    private TopicRetryService retries;
    @org.springframework.beans.factory.annotation.Autowired
    private TopicAttemptPresenter retryPresenter;
    private final LocalLearnerProvider learner;
    private final TopicLearnerCatalogService catalog;
    private final UserTopicItemAttemptRepository attempts;
    private final TopicItemVersionRepository versions;
    private final MongoTemplate mongo;
    private final TopicItemRepository topicItems;
    private final TopicLearningService learning;
    private final TopicQuizMapper quizMapper;
    private final TopicRepository topics;
    private final TopicVersionRepository releases;

    public TopicAttemptService(LocalLearnerProvider learner, TopicLearnerCatalogService catalog,
            UserTopicItemAttemptRepository attempts, TopicItemVersionRepository versions, MongoTemplate mongo,
            TopicItemRepository topicItems, TopicLearningService learning, TopicQuizMapper quizMapper,
            TopicRepository topics, TopicVersionRepository releases) {
        this.learner = learner; this.catalog = catalog; this.attempts = attempts; this.versions = versions; this.mongo = mongo;
        this.topicItems = topicItems; this.learning = learning; this.quizMapper = quizMapper;
        this.topics = topics; this.releases = releases;
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
        if ("RETRY".equals(body.mode())) return retries.startItem(itemId, body, key);
        if (body.retrySessionId() != null || body.replaceRetrySessionId() != null) throw problem(400, "INVALID_REQUEST", "LEARN cannot attach a retry session");
        String signature = "start|" + itemId + "|" + body.mode();
        String id = hash(userId + "|" + key).substring(0, 24);
        UserTopicItemAttempt previous = attempts.findById(id).orElse(null);
        if (previous != null) {
            if (!userId.equals(previous.getUserId()) || !signature.equals(previous.getStartFingerprint())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another action");
            return describe(previous, LearningStage.CONTEXT, 0, 0, previous.getStartedAt());
        }
        TopicItem storedItem = topicItems.findById(itemId).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Topic item not found"));
        reconcileHiddenRunning(userId, storedItem.getTopicId());
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

    public TopicAttemptResponse get(String id) {
        UserTopicItemAttempt attempt = owned(id);
        requireItemVisible(attempt, version(attempt));
        if (attempt.getStatus() == AttemptStatus.ABANDONED) throw problem(409, "STATE_CHANGED", "This attempt is no longer active");
        return describe(attempt);
    }

    public TopicItemContentResponse content(String id) {
        UserTopicItemAttempt attempt = owned(id);
        TopicItemVersion pinned = version(attempt);
        requireItemVisible(attempt, pinned);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw problem(409, "STATE_CHANGED", "Completed content is available through review");
        return TopicAttemptContentMapper.content(pinned);
    }

    public CurrentQuizResponse getCurrentQuiz(String id) {
        UserTopicItemAttempt attempt = owned(id);
        TopicItemVersion pinned = version(attempt);
        requireItemVisible(attempt, pinned);
        if (attempt.getStatus() == AttemptStatus.COMPLETED) return new CurrentQuizResponse(describe(attempt), null);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw problem(409, "STATE_CHANGED", "This attempt is no longer active");
        requireQuizFrontier(attempt);
        List<TopicQuizMapper.QuizEntry> sequence = quizMapper.sequence(pinned);
        Optional<TopicQuizMapper.QuizEntry> current = firstUnanswered(sequence, attempt.getQuizProgress());
        return new CurrentQuizResponse(describe(attempt), current.map(quizMapper::toDto).orElse(null));
    }

    public TopicAttemptResponse advance(String id, AdvanceTopicContentRequest body, String key) {
        if (isRetry(id)) return retries.advance(id, body, key);
        UserTopicItemAttempt attempt = owned(id);
        String fingerprint = "advance|" + body.revision() + "|" + body.stage() + "|" + body.itemIndex();
        ContentMutationReceiptVo replay = receipt(attempt, key, fingerprint);
        if (replay != null) return describeReceipt(attempt, replay);
        requireItemVisible(attempt, version(attempt));
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
            LearningStage quizStage = firstQuizStage(version(attempt));
            if (quizStage == null) throw problem(422, "CONTENT_UNAVAILABLE", "This item has no published supported exercise");
            next = new TopicCatalogPolicy.Position(quizStage.name(), 0);
        }
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        long revision = revision(attempt) + 1;
        ContentMutationReceiptVo saved = new ContentMutationReceiptVo(key, fingerprint, LearningStage.valueOf(next.stage()), next.itemIndex(), revision, now, true);
        Query query = cas(attempt);
        Update update = new Update().set("furthest_reached", FurthestReachedVo.builder().stage(saved.stage()).itemIndex(saved.itemIndex()).build())
                .set("version", revision).set("last_accessed_at", now).push("mutation_receipts", saved);
        UserTopicItemAttempt result = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
        if (result == null) {
            UserTopicItemAttempt latest = owned(id);
            ContentMutationReceiptVo concurrentReplay = receipt(latest, key, fingerprint);
            if (concurrentReplay != null) return describeReceipt(latest, concurrentReplay);
            throw problem(409, "STATE_CHANGED", "Another request changed the position");
        }
        return describe(result);
    }

    public QuizAnswerResponse answerQuiz(String id, String quizId, AnswerQuizRequest body, String key) {
        if (isRetry(id)) return retries.answer(id, quizId, body, key);
        UserTopicItemAttempt attempt = owned(id);
        if (key == null || key.isBlank() || key.length() > 128) throw problem(400, "INVALID_REQUEST", "Idempotency-Key is required");
        String fingerprint = "answer|" + body.revision() + "|" + quizId + "|" + body.optionId();
        ContentMutationReceiptVo replay = receipt(attempt, key, fingerprint);
        if (replay != null) return replayedAnswer(attempt, quizId, body, replay);
        TopicItemVersion pinned = version(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw problem(409, "STATE_CHANGED", "Reload the saved position before answering");
        requireItemVisible(attempt, pinned);
        requireQuizFrontier(attempt);
        List<TopicQuizMapper.QuizEntry> sequence = quizMapper.sequence(pinned);
        TopicQuizMapper.QuizEntry entry = quizMapper.find(sequence, quizId)
                .orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Unknown quiz"));
        TopicQuizMapper.QuizEntry current = firstUnanswered(sequence, attempt.getQuizProgress())
                .orElseThrow(() -> problem(409, "STATE_CHANGED", "Reload before answering"));
        if (!current.quizId().equals(entry.quizId())) throw problem(409, "STATE_CHANGED", "Reload before answering");
        if (!Objects.equals(revision(attempt), body.revision())) throw problem(409, "STATE_CHANGED", "Reload before answering");
        requireOptionOf(entry, body.optionId());
        boolean correct = body.optionId().equals(quizMapper.correctOptionId(entry));
        QuizFeedbackResponse feedback = correct ? quizMapper.feedback(entry) : null;
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        long newRevision = revision(attempt) + 1;
        List<QuizProgressVo> progress = answeredQuizProgress(attempt.getQuizProgress(), quizId, body.optionId(), correct, now);
        FurthestReachedVo advanced = correct ? nextFrontier(entry, sequence) : null;
        boolean completed = correct && firstUnanswered(sequence, progress).isEmpty();
        FurthestReachedVo frontier = attempt.getFurthestReached();
        ContentMutationReceiptVo saved = new ContentMutationReceiptVo(key, fingerprint,
                completed ? null : advanced != null ? advanced.getStage() : frontier == null ? LearningStage.CONTEXT : frontier.getStage(),
                advanced != null ? advanced.getItemIndex() : frontier == null ? 0 : frontier.getItemIndex(),
                newRevision, now, true);
        Update update = new Update()
                .set("quiz_progress", progress)
                .set("version", newRevision)
                .set("last_accessed_at", now)
                .push("mutation_receipts", saved);
        if (advanced != null) update.set("furthest_reached", advanced);
        if (completed) update.set("status", AttemptStatus.COMPLETED).set("furthest_reached", null)
                .set("completed_at", now).set("completion_result", completionResult(pinned, sequence, progress));
        UserTopicItemAttempt result = mongo.findAndModify(cas(attempt), update,
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
        if (result == null) {
            UserTopicItemAttempt latest = owned(id);
            ContentMutationReceiptVo concurrentReplay = receipt(latest, key, fingerprint);
            if (concurrentReplay != null) return replayedAnswer(latest, quizId, body, concurrentReplay);
            throw problem(409, "STATE_CHANGED", "Another request changed the attempt while answering");
        }
        return new QuizAnswerResponse(quizId, correct, feedback, describe(result));
    }

    public TopicAttemptResponse complete(String id, CompleteTopicAttemptRequest body, String key) {
        if (isRetry(id)) throw problem(409, "STATE_CHANGED", "Retry completion is committed with the final answer");
        UserTopicItemAttempt attempt = owned(id);
        String fingerprint = "complete|" + body.revision();
        ContentMutationReceiptVo replay = receipt(attempt, key, fingerprint);
        if (replay != null) return describeReceipt(attempt, replay);
        TopicItemVersion pinned = version(attempt);
        requireItemVisible(attempt, pinned);
        if (revision(attempt) != body.revision() || attempt.getStatus() == AttemptStatus.ABANDONED)
            throw problem(409, "STATE_CHANGED", "Reload the saved attempt before completing");
        if (attempt.getStatus() == AttemptStatus.COMPLETED) throw problem(409, "STATE_CHANGED", "This attempt is already completed; reload its result");
        requireQuizFrontier(attempt);
        List<TopicQuizMapper.QuizEntry> sequence = quizMapper.sequence(pinned);
        if (sequence.isEmpty() || firstUnanswered(sequence, attempt.getQuizProgress()).isPresent())
            throw problem(409, "ATTEMPT_NOT_COMPLETED", "Answer every quiz correctly before completing");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        ContentMutationReceiptVo saved = new ContentMutationReceiptVo(key, fingerprint, null, 0, revision(attempt) + 1, now, true);
        UserTopicItemAttempt result = mongo.findAndModify(cas(attempt), new Update()
                .set("status", AttemptStatus.COMPLETED).set("furthest_reached", null).set("completed_at", now)
                .set("completion_result", completionResult(pinned, sequence, attempt.getQuizProgress()))
                .set("version", saved.revision()).set("last_accessed_at", now).push("mutation_receipts", saved),
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
        if (result == null) {
            UserTopicItemAttempt latest = owned(id);
            ContentMutationReceiptVo concurrentReplay = receipt(latest, key, fingerprint);
            if (concurrentReplay != null) return describeReceipt(latest, concurrentReplay);
            throw problem(409, "STATE_CHANGED", "Another request changed this attempt");
        }
        return describe(result);
    }

    private CompletionResultVo completionResult(TopicItemVersion pinned, List<TopicQuizMapper.QuizEntry> sequence,
            List<QuizProgressVo> progress) {
        if (sequence.isEmpty() || firstUnanswered(sequence, progress).isPresent())
            throw problem(409, "ATTEMPT_NOT_COMPLETED", "Quiz results are incomplete");
        int firstTry = 0;
        for (TopicQuizMapper.QuizEntry quiz : sequence) {
            QuizProgressVo saved = progress.stream().filter(value -> value != null && quiz.quizId().equals(value.getQuizId())).findFirst().orElseThrow();
            if (saved.getSubmissions() != null && !saved.getSubmissions().isEmpty() && Boolean.TRUE.equals(saved.getSubmissions().get(0).getIsCorrect())) firstTry++;
        }
        return new CompletionResultVo(pinned.getTitle(), firstTry, sequence.size());
    }

    public TopicItemResultResponse itemResult(String id) {
        UserTopicItemAttempt attempt = isRetry(id) ? retries.owned(id, true) : owned(id);
        requireItemVisible(attempt, version(attempt));
        if (attempt.getStatus() != AttemptStatus.COMPLETED) throw problem(409, "ATTEMPT_NOT_COMPLETED", "This attempt has not completed");
        TopicDetailResponse detail = catalog.detail(attempt.getTopicId());
        int index = -1;
        for (int i = 0; i < detail.getItems().size(); i++) if (attempt.getTopicItemId().equals(detail.getItems().get(i).getTopicItemId())) index = i;
        if (index < 0) throw problem(410, "ITEM_HIDDEN", "The item is no longer available");
        if (!id.equals(detail.getItems().get(index).getCompletedAttemptId())) throw problem(409, "RESULT_CHANGED", "Open the current result from the topic");
        CompletionResultVo result = resultData(attempt);
        TopicItemSummaryResponse next = index + 1 < detail.getItems().size() ? detail.getItems().get(index + 1) : null;
        TopicCatalogPolicy.Availability availability = next == null ? new TopicCatalogPolicy.Availability(false, "CONTENT_UNAVAILABLE")
                : next.getActions().continueAction().allowed() ? next.getActions().continueAction() : next.getActions().start();
        TopicRetrySessionResponse retrySession = null;
        if (attempt.getAttemptType() == AttemptType.RETRY) {
            TopicRetryRuntime runtime = retries.runtime(attempt.getTopicId());
            next = null;
            availability = new TopicCatalogPolicy.Availability(false, "CONTENT_UNAVAILABLE");
            if (runtime != null && Objects.equals(runtime.getSessionId(), attempt.getUserTopicAttemptId())) {
                retrySession = TopicRetryService.session(runtime);
                String nextId = retrySession.nextTopicItemId();
                next = nextId == null ? null : detail.getItems().stream().filter(item -> nextId.equals(item.getTopicItemId())).findFirst().orElse(null);
                boolean allowed = next != null && "IN_PROGRESS".equals(runtime.getStatus());
                availability = new TopicCatalogPolicy.Availability(allowed, allowed ? null : "CONTENT_UNAVAILABLE");
            }
        }
        return new TopicItemResultResponse(id, attempt.getTopicId(), attempt.getTopicItemId(), result.title(),
                result.firstTryCorrectCount(), result.totalQuizCount(), accuracy(result), attempt.getCompletedAt(),
                next == null ? null : next.getTopicItemId(), availability, retrySession);
    }

    public TopicItemReviewResponse review(String id) {
        UserTopicItemAttempt attempt = isRetry(id) ? retries.owned(id, true) : owned(id);
        TopicItemVersion pinned = version(attempt);
        requireItemVisible(attempt, pinned);
        if (attempt.getStatus() != AttemptStatus.COMPLETED) throw problem(409, "ATTEMPT_NOT_COMPLETED", "Complete this attempt before review");
        TopicDetailResponse detail = catalog.detail(attempt.getTopicId());
        if (detail.getItems().stream().noneMatch(item -> attempt.getTopicItemId().equals(item.getTopicItemId()) && id.equals(item.getCompletedAttemptId())))
            throw problem(409, "RESULT_CHANGED", "Open the current completed result");
        List<TopicItemReviewResponse.ReviewedQuiz> reviewed = new ArrayList<>();
        for (var entry : quizMapper.sequence(pinned)) {
            QuizProgressVo saved = attempt.getQuizProgress() == null ? null : attempt.getQuizProgress().stream()
                    .filter(value -> value != null && entry.quizId().equals(value.getQuizId())).findFirst().orElse(null);
            if (saved == null || saved.getSubmissions() == null || saved.getSubmissions().isEmpty())
                throw problem(404, "CONTENT_UNAVAILABLE", "Stored quiz history is unavailable for review");
            reviewed.add(new TopicItemReviewResponse.ReviewedQuiz(quizMapper.toDto(entry), quizMapper.feedback(entry),
                    Boolean.TRUE.equals(saved.getSubmissions().get(0).getIsCorrect())));
        }
        return new TopicItemReviewResponse(describe(attempt), TopicAttemptContentMapper.content(pinned), reviewed);
    }

    public TopicResultResponse topicResult(String topicId) {
        userId();
        TopicDetailResponse detail = catalog.detail(topicId);
        if (!"COMPLETED".equals(detail.getProgress().status()) || detail.getItems().isEmpty())
            throw problem(409, "TOPIC_NOT_COMPLETED", "Complete every visible item before opening the topic result");
        List<TopicResultResponse.Item> results = new ArrayList<>();
        int firstTry = 0, total = 0;
        for (TopicItemSummaryResponse item : detail.getItems()) {
            UserTopicItemAttempt attempt = owned(item.getCompletedAttemptId());
            requireItemVisible(attempt, version(attempt));
            CompletionResultVo result = resultData(attempt);
            firstTry += result.firstTryCorrectCount(); total += result.totalQuizCount();
            results.add(new TopicResultResponse.Item(item.getTopicItemId(), item.getTitle(), attempt.getId(),
                    result.firstTryCorrectCount(), result.totalQuizCount(), accuracy(result), item.isHasNewContent(), item.getActions()));
        }
        return new TopicResultResponse(topicId, detail.getTitleVi(), firstTry, total, (int)Math.round(100.0 * firstTry / total), results, detail.getRetryAvailability());
    }

    private CompletionResultVo resultData(UserTopicItemAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.COMPLETED) throw problem(409, "RESULT_CHANGED", "The completed result is unavailable");
        if (attempt.getCompletionResult() != null) return attempt.getCompletionResult();
        UserTopicItemProgress legacy = mongo.findOne(new Query(Criteria.where("user_id").is(attempt.getUserId())
                .and("topic_item_id").is(attempt.getTopicItemId()).and("last_completed_attempt_id").is(attempt.getId())), UserTopicItemProgress.class);
        if (legacy == null || legacy.getFirstTryCorrectCount() == null || legacy.getTotalQuizCount() == null || legacy.getTotalQuizCount() < 1)
            throw problem(409, "RESULT_CHANGED", "Stored result counts are unavailable");
        return new CompletionResultVo(version(attempt).getTitle(), legacy.getFirstTryCorrectCount(), legacy.getTotalQuizCount());
    }

    private int accuracy(CompletionResultVo result) { return (int)Math.round(100.0 * result.firstTryCorrectCount() / result.totalQuizCount()); }

    private void reconcileHiddenRunning(String userId, String topicId) {
        for (UserTopicItemAttempt attempt : attempts.findByUserIdAndTopicId(userId, topicId)) {
            if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) continue;
            try { requireItemVisible(attempt, version(attempt)); }
            catch (TopicRequestException unavailable) {
                if (unavailable.getStatus() != 410) throw unavailable;
                mongo.findAndModify(cas(attempt), new Update().set("status", AttemptStatus.ABANDONED)
                        .set("abandoned_reason", "ITEM_HIDDEN").set("version", revision(attempt) + 1), UserTopicItemAttempt.class);
            }
        }
    }

    private QuizAnswerResponse replayedAnswer(UserTopicItemAttempt attempt, String quizId, AnswerQuizRequest body,
            ContentMutationReceiptVo replay) {
        TopicItemVersion pinned = version(attempt);
        TopicQuizMapper.QuizEntry entry = quizMapper.find(quizMapper.sequence(pinned), quizId)
                .orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Unknown quiz"));
        QuizSubmissionVo matched = null;
        if (attempt.getQuizProgress() != null) for (QuizProgressVo state : attempt.getQuizProgress()) {
            if (state == null || !quizId.equals(state.getQuizId()) || state.getSubmissions() == null) continue;
            List<QuizSubmissionVo> submissions = state.getSubmissions();
            for (int i = submissions.size() - 1; i >= 0 && matched == null; i--) {
                QuizSubmissionVo submission = submissions.get(i);
                if (submission != null && body.optionId().equals(String.valueOf(submission.getAnswer()))) matched = submission;
            }
            if (matched != null) break;
        }
        if (matched == null) throw problem(409, "STATE_CHANGED", "Reload the saved position before answering");
        boolean correct = Boolean.TRUE.equals(matched.getIsCorrect());
        TopicAttemptResponse attemptDto = describeReceipt(attempt, replay);
        return new QuizAnswerResponse(quizId, correct, correct ? quizMapper.feedback(entry) : null, attemptDto);
    }

    private void requireOptionOf(TopicQuizMapper.QuizEntry entry, String optionId) {
        String prefix = entry.quizId() + ":option:";
        String suffix = optionId == null || !optionId.startsWith(prefix) ? null : optionId.substring(prefix.length());
        int position = -1;
        if (suffix != null && !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
            try { position = Integer.parseInt(suffix); } catch (NumberFormatException overflow) { position = -1; }
        }
        List<QuizOptionVo> options = entry.question().getOptions();
        if (position < 0 || options == null || position >= options.size())
            throw problem(400, "INVALID_REQUEST", "optionId does not belong to the current quiz");
    }

    private FurthestReachedVo nextFrontier(TopicQuizMapper.QuizEntry entry, List<TopicQuizMapper.QuizEntry> sequence) {
        if (entry.indexInType() + 1 < entry.totalInType()) {
            return FurthestReachedVo.builder()
                    .stage(entry.type() == QuizType.FILL_BLANK ? LearningStage.FILL_BLANK : LearningStage.SITUATIONAL_CHOICE)
                    .itemIndex(entry.indexInType() + 1).build();
        }
        if (entry.type() == QuizType.FILL_BLANK && sequence.stream()
                .anyMatch(other -> other != null && other.type() == QuizType.SITUATIONAL_CHOICE)) {
            return FurthestReachedVo.builder().stage(LearningStage.SITUATIONAL_CHOICE).itemIndex(0).build();
        }
        return null;
    }

    private List<QuizProgressVo> answeredQuizProgress(List<QuizProgressVo> current, String quizId, String optionId,
            boolean correct, Instant now) {
        List<QuizProgressVo> copy = new ArrayList<>();
        if (current != null) for (QuizProgressVo state : current) {
            if (state == null) { copy.add(null); continue; }
            List<QuizSubmissionVo> submissions = state.getSubmissions() == null
                    ? new ArrayList<QuizSubmissionVo>() : new ArrayList<>(state.getSubmissions());
            copy.add(QuizProgressVo.builder().quizId(state.getQuizId()).completedAt(state.getCompletedAt())
                    .submissions(submissions).build());
        }
        QuizProgressVo state = copy.stream().filter(entry -> entry != null && quizId.equals(entry.getQuizId()))
                .findFirst().orElse(null);
        if (state == null) {
            state = QuizProgressVo.builder().quizId(quizId).submissions(new ArrayList<QuizSubmissionVo>()).build();
            copy.add(state);
        }
        state.getSubmissions().add(QuizSubmissionVo.builder().answer(optionId).isCorrect(correct).submittedAt(now).build());
        if (correct && state.getCompletedAt() == null) state.setCompletedAt(now);
        return copy;
    }

    public Map<String, Instant> access(String id, String key) {
        if (isRetry(id)) return retries.access(id, key);
        UserTopicItemAttempt attempt = owned(id);
        requireItemVisible(attempt, version(attempt));
        if (attempt.getStatus() == AttemptStatus.ABANDONED) throw problem(409, "STATE_CHANGED", "This attempt is no longer active");
        ContentMutationReceiptVo replay = receipt(attempt, key, "access");
        if (replay != null) return Map.of("lastAccessedAt", replay.accessedAt());
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        ContentMutationReceiptVo saved = new ContentMutationReceiptVo(key, "access", null, 0, revision(attempt), now, true);
        UserTopicItemAttempt result = mongo.findAndModify(cas(attempt),
                new Update().set("version", attempt.getVersion()).set("last_accessed_at", now).push("mutation_receipts", saved),
                FindAndModifyOptions.options().returnNew(true), UserTopicItemAttempt.class);
        if (result == null) throw problem(409, "STATE_CHANGED", "Attempt changed while recording access");
        return Map.of("lastAccessedAt", now);
    }

    public TopicAttemptResponse describe(UserTopicItemAttempt attempt) {
        if (attempt.getAttemptType() == AttemptType.RETRY) return retryPresenter.describe(attempt);
        FurthestReachedVo position = attempt.getFurthestReached();
        return describe(attempt, position == null ? null : position.getStage(), position == null ? 0 : position.getItemIndex(), revision(attempt), attempt.getLastAccessedAt());
    }

    private TopicAttemptResponse describe(UserTopicItemAttempt attempt, LearningStage stage, int index, long revision, Instant accessed) {
        return describe(attempt, stage, index, revision, accessed, true);
    }

    private TopicAttemptResponse describeReceipt(UserTopicItemAttempt attempt, ContentMutationReceiptVo receipt) {
        return describe(attempt, receipt.stage(), receipt.itemIndex(), receipt.revision(), receipt.accessedAt(), Boolean.TRUE.equals(receipt.roundedProgress()));
    }

    private TopicAttemptResponse describe(UserTopicItemAttempt attempt, LearningStage stage, int index, long revision, Instant accessed, boolean rounded) {
        TopicItemVersion version = version(attempt);
        TopicItemContentResponse content = TopicAttemptContentMapper.content(version);
        List<TopicCatalogPolicy.Position> positions = contentPositions(content);
        int totalQuiz = quizCount(version, QuizType.FILL_BLANK) + quizCount(version, QuizType.SITUATIONAL_CHOICE);
        int completed = stage == null && attempt.getStatus() == AttemptStatus.COMPLETED ? positions.size() + totalQuiz : positions.indexOf(new TopicCatalogPolicy.Position(stage == null ? "CONTEXT" : stage.name(), index));
        if (completed < 0) completed = positions.size() + (stage == LearningStage.SITUATIONAL_CHOICE ? quizCount(version, QuizType.FILL_BLANK) : 0) + index;
        int learnedWords = 0, learnedExpressions = 0;
        for (int i = 0; i < Math.min(completed, positions.size()); i++) {
            TopicCatalogPolicy.Position position = positions.get(i);
            if ("VOCAB".equals(position.stage())) learnedWords++;
            if ("EXPRESSIONS".equals(position.stage())) learnedExpressions += purposes(content).get(position.itemIndex()).getExamples().size();
        }
        int total = positions.size() + totalQuiz;
        int doneQuiz = Math.max(0, completed - positions.size());
        int percent = total == 0 ? 0 : rounded ? (int)Math.round(100.0 * completed / total) : (int)(100L * completed / total);
        TopicAttemptResponse.Progress progress = new TopicAttemptResponse.Progress(completed, total, percent,
                learnedWords, learnedExpressions, doneQuiz, totalQuiz);
        return new TopicAttemptResponse(attempt.getId(), attempt.getTopicId(), attempt.getTopicItemId(), attempt.getTopicItemVersionId(),
                version.getVersion(), "LEARN", stage == null ? attempt.getStatus().name() : "IN_PROGRESS", revision,
                stage == null ? null : new TopicCatalogPolicy.Position(stage.name(), index), progress, null, attempt.getStartedAt(), accessed, stage == null ? attempt.getCompletedAt() : null);
    }

    public TopicProgressResponse topicProgress(String topicId) {
        userId();
        TopicDetailResponse detail = catalog.detail(topicId);
        TopicCatalogPolicy.ProgressSummary summary = detail.getProgress();
        TopicProgressResponse response = TopicProgressResponse.builder().topicId(topicId)
                .status(ItemProgressStatus.valueOf(summary.status())).completedItemCount(summary.completedItemCount())
                .totalItemCount(summary.totalItemCount()).items(new ArrayList<>()).build();
        List<UserTopicItemAttempt> saved = new ArrayList<>(attempts.findByUserIdAndTopicId(userId(), topicId));
        TopicRetryRuntime retryState = retries == null ? null : retries.runtime(topicId);
        if (retryState != null) saved.addAll(retryState.getOfficialResults().values());
        int totalWords = 0, totalExpressions = 0, totalQuiz = 0, learnedWords = 0, learnedExpressions = 0, doneQuiz = 0;
        Instant accessed = saved.stream().map(UserTopicItemAttempt::getLastAccessedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        for (TopicItemSummaryResponse item : detail.getItems()) {
            UserTopicItemAttempt active = saved.stream().filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS &&
                    a.getAttemptType() == AttemptType.LEARNING && item.getTopicItemId().equals(a.getTopicItemId())).findFirst().orElse(null);
            UserTopicItemAttempt completedAttempt = saved.stream().filter(a -> a.getId().equals(item.getCompletedAttemptId())).findFirst().orElse(null);
            String effectiveVersion = active != null ? active.getTopicItemVersionId() : completedAttempt != null ? completedAttempt.getTopicItemVersionId() : item.getPublishedVersionId();
            TopicItemVersion pinned = versions.findById(effectiveVersion).orElseThrow(
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
            response.getItems().add(TopicItemProgressResponse.builder().topicItemId(item.getTopicItemId())
                    .status(ItemProgressStatus.valueOf(item.getLearningStatus())).completedAt(completedAttempt == null ? null : completedAttempt.getCompletedAt())
                    .firstTryCorrectCount(completedAttempt == null || completedAttempt.getCompletionResult() == null ? null : completedAttempt.getCompletionResult().firstTryCorrectCount())
                    .totalQuizCount(completedAttempt == null || completedAttempt.getCompletionResult() == null ? null : completedAttempt.getCompletionResult().totalQuizCount()).build());
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

    static LearningStage firstQuizStage(TopicItemVersion version) {
        return quizCount(version, QuizType.FILL_BLANK) > 0 ? LearningStage.FILL_BLANK
                : quizCount(version, QuizType.SITUATIONAL_CHOICE) > 0 ? LearningStage.SITUATIONAL_CHOICE : null;
    }

    public static int quizCount(TopicItemVersion version, QuizType type) {
        return version.getQuizzes() == null ? 0 : version.getQuizzes().stream().filter(module -> module.getQuizType() == type)
                .mapToInt(module -> module.getQuestions() == null ? 0 : module.getQuestions().size()).sum();
    }

    static Optional<TopicQuizMapper.QuizEntry> firstUnanswered(List<TopicQuizMapper.QuizEntry> sequence,
            List<QuizProgressVo> progress) {
        if (sequence == null) return Optional.empty();
        for (TopicQuizMapper.QuizEntry entry : sequence) {
            if (entry == null || entry.quizId() == null) continue;
            if (!answeredCorrectly(entry, progress)) return Optional.of(entry);
        }
        return Optional.empty();
    }

    private static boolean answeredCorrectly(TopicQuizMapper.QuizEntry entry, List<QuizProgressVo> progress) {
        if (progress == null) return false;
        for (QuizProgressVo state : progress) {
            if (state == null || !entry.quizId().equals(state.getQuizId()) || state.getSubmissions() == null) continue;
            for (QuizSubmissionVo submission : state.getSubmissions()) {
                if (submission != null && Boolean.TRUE.equals(submission.getIsCorrect())) return true;
            }
        }
        return false;
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
        if (isRetry(id)) return retries.owned(id, false);
        String owner = userId();
        UserTopicItemAttempt attempt = attempts.findById(id).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Attempt not found"));
        if (!owner.equals(attempt.getUserId())) throw problem(404, "CONTENT_UNAVAILABLE", "Attempt not found");
        return attempt;
    }

    private boolean isRetry(String id) { return retries != null && retries.isRetry(id); }

    private TopicItemVersion version(UserTopicItemAttempt attempt) {
        return versions.findById(attempt.getTopicItemVersionId()).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Saved content version not found"));
    }

    private void requireQuizFrontier(UserTopicItemAttempt attempt) {
        FurthestReachedVo frontier = attempt.getFurthestReached();
        boolean reachedQuiz = frontier != null && (frontier.getStage() == LearningStage.FILL_BLANK
                || frontier.getStage() == LearningStage.SITUATIONAL_CHOICE);
        if (!reachedQuiz) throw problem(409, "STATE_CHANGED", "Finish the item content before the quiz");
    }

    private void requireItemVisible(UserTopicItemAttempt attempt, TopicItemVersion pinned) {
        if (pinned.getStatus() == ContentStatus.HIDDEN) throw problem(410, "ITEM_HIDDEN", "The item is no longer available");
        Topic topic = topics.findById(attempt.getTopicId()).orElseThrow(() -> problem(410, "ITEM_HIDDEN", "The item is no longer available"));
        String latest = topic.getLatestPublishedVersion();
        if (latest == null || latest.isBlank()) throw problem(410, "ITEM_HIDDEN", "The item is no longer available");
        TopicVersion release = releases.findById(latest).orElseThrow(() -> problem(410, "ITEM_HIDDEN", "The item is no longer available"));
        if (release.getStatus() != ContentStatus.PUBLISHED) throw problem(410, "ITEM_HIDDEN", "The item is no longer available");
        List<String> itemVersionIds = release.getItemVersionIds();
        if (itemVersionIds == null || itemVersionIds.isEmpty()) throw problem(410, "ITEM_HIDDEN", "The item is no longer available");
        boolean stillPublished = versions.findAllById(new ArrayList<>(new LinkedHashSet<>(itemVersionIds))).stream()
                .anyMatch(itemVersion -> pinned.getTopicItemId().equals(itemVersion.getTopicItemId())
                        && itemVersion.getStatus() != ContentStatus.HIDDEN);
        if (!stillPublished) throw problem(410, "ITEM_HIDDEN", "The item is no longer available");
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
