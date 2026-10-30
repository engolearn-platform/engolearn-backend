package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.infra.dev.LocalLearnerProvider;
import fit.iuh.engolearn.topic.service.*;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.topic.dto.request.*;
import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicAttemptContentMapper;
import fit.iuh.engolearn.topic.mapper.TopicAttemptMapper;
import fit.iuh.engolearn.topic.mapper.TopicRetrySessionMapper;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.model.vo.*;
import fit.iuh.engolearn.topic.repository.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class TopicAttemptServiceImpl implements TopicAttemptService {
    private final LocalLearnerProvider learner;
    private final TopicLearnerCatalogService catalog;
    private final UserTopicItemAttemptRepository attempts;
    private final TopicItemVersionRepository versions;
    private final TopicAttemptMutationRepository mutations;
    private final TopicItemRepository topicItems;
    private final TopicLearningService learning;
    private final TopicQuizMapper quizMapper;
    private final TopicRepository topics;
    private final TopicVersionRepository releases;
    private final TopicGenerationService generations;
    private final TopicRetryService retries;
    private final TopicAttemptMapper attemptMapper;
    private final TopicQuizProgressService quizProgress;
    private final TopicRetrySessionMapper retrySessionMapper;

    @Override
    public TopicAttemptResponse start(String itemId, StartTopicAttemptRequest body, String key) {
        String userId = userId();
        if ("RETRY".equals(body.mode())) return retries.startItem(itemId, body, key);
        if (body.retrySessionId() != null || body.replaceRetrySessionId() != null) throw problem(400, "INVALID_REQUEST", "LEARN cannot attach a retry session");
        String signature = "start|" + itemId + "|" + body.mode();
        TopicItem storedItem = topicItems.findById(itemId).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Topic item not found"));
        long generation = generations.current(userId, storedItem.getTopicId());
        String id = hash(userId + "|" + key + (generation == 0 ? "" : "|" + storedItem.getTopicId() + "|generation|" + generation)).substring(0, 24);
        UserTopicItemAttempt previous = attempts.findById(id).orElse(null);
        if (previous != null) {
            if (!userId.equals(previous.getUserId()) || !signature.equals(previous.getStartFingerprint())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another action");
            requireGeneration(previous);
            return describe(previous, LearningStage.CONTEXT, 0, 0, previous.getStartedAt());
        }
        UserTopicItemAttempt oldKey = mutations.findByStartKey(userId, storedItem.getTopicId(), key);
        if (oldKey != null && userId.equals(oldKey.getUserId())) requireGeneration(oldKey);
        reconcileHiddenRunning(userId, storedItem.getTopicId());
        TopicDetailResponse detail = catalog.detail(storedItem.getTopicId());
        TopicItemSummaryResponse item = detail.getItems().stream().filter(row -> row.getTopicItemId().equals(itemId)).findFirst()
                .orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Topic item is not published"));
        if (item.getLearningAttempt() != null) return describe(owned(item.getLearningAttempt().attemptId()));
        if (!item.getActions().start().allowed()) throw problem(422, item.getActions().start().reason(), "The item cannot be started");
        TopicItemVersion pinned = versions.findById(item.getPublishedVersionId()).orElseThrow(() -> problem(404, "CONTENT_UNAVAILABLE", "Published version not found"));
        TopicAttemptContentMapper.content(pinned);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        UserTopicItemAttempt created = UserTopicItemAttempt.builder().id(id).generation(generation).userId(userId).topicId(detail.getTopicId()).topicItemId(itemId)
                .topicItemVersionId(pinned.getId()).attemptType(AttemptType.LEARNING).startRequestKey(key).startFingerprint(signature)
                .mutationReceipts(new ArrayList<>()).status(AttemptStatus.IN_PROGRESS)
                .furthestReached(FurthestReachedVo.builder().stage(LearningStage.CONTEXT).itemIndex(0).build())
                .startedAt(now).lastAccessedAt(now).build();
        try { return describe(attempts.insert(created)); }
        catch (DuplicateKeyException conflict) {
            UserTopicItemAttempt keyed = attempts.findById(id).orElse(null);
            if (keyed != null && !signature.equals(keyed.getStartFingerprint())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another action");
            return attempts.findByUserIdAndTopicId(userId, detail.getTopicId()).stream()
                    .filter(a -> a.getGeneration() == generation && a.getStatus() == AttemptStatus.IN_PROGRESS && a.getAttemptType() == AttemptType.LEARNING && itemId.equals(a.getTopicItemId()))
                    .findFirst().map(this::describe).orElseThrow(() -> problem(409, "OTHER_ITEM_IN_PROGRESS", "Another item was started"));
        }
    }

    @Override
    public TopicAttemptResponse get(String id) {
        UserTopicItemAttempt attempt = owned(id);
        requireItemVisible(attempt, version(attempt));
        if (attempt.getStatus() == AttemptStatus.ABANDONED) throw problem(409, "STATE_CHANGED", "This attempt is no longer active");
        return describe(attempt);
    }

    @Override
    public TopicItemContentResponse content(String id) {
        UserTopicItemAttempt attempt = owned(id);
        TopicItemVersion pinned = version(attempt);
        requireItemVisible(attempt, pinned);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw problem(409, "STATE_CHANGED", "Completed content is available through review");
        return TopicAttemptContentMapper.content(pinned);
    }

    @Override
    public CurrentQuizResponse getCurrentQuiz(String id) {
        UserTopicItemAttempt attempt = owned(id);
        TopicItemVersion pinned = version(attempt);
        requireItemVisible(attempt, pinned);
        if (attempt.getStatus() == AttemptStatus.COMPLETED) return new CurrentQuizResponse(describe(attempt), null);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw problem(409, "STATE_CHANGED", "This attempt is no longer active");
        requireQuizFrontier(attempt);
        List<TopicQuizMapper.QuizEntry> sequence = quizMapper.sequence(pinned);
        Optional<TopicQuizMapper.QuizEntry> current = quizProgress.firstUnanswered(sequence, attempt.getQuizProgress());
        return new CurrentQuizResponse(describe(attempt), current.map(quizMapper::toDto).orElse(null));
    }

    @Override
    public QuizNavigationResponse quizNavigation(String id) {
        UserTopicItemAttempt attempt = owned(id);
        TopicItemVersion pinned = version(attempt);
        requireItemVisible(attempt, pinned);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw problem(409, "STATE_CHANGED", "Open completed exercises through review");
        var sequence = quizMapper.sequence(pinned);
        var frontier = attempt.getFurthestReached();
        boolean reached = frontier != null && (frontier.getStage() == LearningStage.FILL_BLANK || frontier.getStage() == LearningStage.SITUATIONAL_CHOICE);
        String current = reached ? quizProgress.firstUnanswered(sequence, attempt.getQuizProgress()).map(TopicQuizMapper.QuizEntry::quizId).orElse(null) : null;
        List<QuizNavigationResponse.Entry> entries = new ArrayList<>();
        for (var entry : sequence) {
            QuizProgressVo progress = attempt.getQuizProgress() == null ? null : attempt.getQuizProgress().stream()
                    .filter(value -> value != null && entry.quizId().equals(value.getQuizId())).findFirst().orElse(null);
            boolean completed = reached && quizProgress.answeredCorrectly(entry, attempt.getQuizProgress());
            boolean selected = entry.quizId().equals(current);
            entries.add(new QuizNavigationResponse.Entry(entry.quizId(), entry.type(), entry.indexInType(),
                    completed ? "COMPLETED" : selected ? "CURRENT" : "LOCKED",
                    completed || selected ? quizMapper.toDto(entry) : null,
                    completed ? quizMapper.feedback(entry) : null,
                    completed ? Boolean.TRUE.equals(progress.getSubmissions().get(0).getIsCorrect()) : null));
        }
        return new QuizNavigationResponse(describe(attempt), entries);
    }

    @Override
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
            LearningStage quizStage = quizProgress.firstQuizStage(version(attempt));
            if (quizStage == null) throw problem(422, "CONTENT_UNAVAILABLE", "This item has no published supported exercise");
            next = new TopicCatalogPolicy.Position(quizStage.name(), 0);
        }
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        long revision = revision(attempt) + 1;
        ContentMutationReceiptVo saved = ContentMutationReceiptVo.builder()
                .key(key)
                .fingerprint(fingerprint)
                .stage(LearningStage.valueOf(next.stage()))
                .itemIndex(next.itemIndex())
                .revision(revision)
                .accessedAt(now)
                .roundedProgress(true)
                .build();
        UserTopicItemAttempt result = mutations.advance(attempt,
                FurthestReachedVo.builder().stage(saved.getStage()).itemIndex(saved.getItemIndex()).build(),
                revision, now, saved);
        if (result == null) {
            UserTopicItemAttempt latest = owned(id);
            ContentMutationReceiptVo concurrentReplay = receipt(latest, key, fingerprint);
            if (concurrentReplay != null) return describeReceipt(latest, concurrentReplay);
            throw problem(409, "STATE_CHANGED", "Another request changed the position");
        }
        return describe(result);
    }

    @Override
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
        TopicQuizMapper.QuizEntry current = quizProgress.firstUnanswered(sequence, attempt.getQuizProgress())
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
        boolean completed = correct && quizProgress.firstUnanswered(sequence, progress).isEmpty();
        FurthestReachedVo frontier = attempt.getFurthestReached();
        ContentMutationReceiptVo saved = ContentMutationReceiptVo.builder()
                .key(key)
                .fingerprint(fingerprint)
                .stage(completed ? null : advanced != null ? advanced.getStage() : frontier == null ? LearningStage.CONTEXT : frontier.getStage())
                .itemIndex(advanced != null ? advanced.getItemIndex() : frontier == null ? 0 : frontier.getItemIndex())
                .revision(newRevision)
                .accessedAt(now)
                .roundedProgress(true)
                .build();
        CompletionResultVo completedResult = completed ? completionResult(pinned, sequence, progress) : null;
        UserTopicItemAttempt result = mutations.recordAnswer(
                attempt, progress, newRevision, now, saved, advanced, completedResult);
        if (result == null) {
            UserTopicItemAttempt latest = owned(id);
            ContentMutationReceiptVo concurrentReplay = receipt(latest, key, fingerprint);
            if (concurrentReplay != null) return replayedAnswer(latest, quizId, body, concurrentReplay);
            throw problem(409, "STATE_CHANGED", "Another request changed the attempt while answering");
        }
        return new QuizAnswerResponse(quizId, correct, feedback, describe(result));
    }

    @Override
    public TopicAttemptResponse complete(String id, CompleteTopicAttemptRequest body, String key) {
        if (isRetry(id)) {
            retries.owned(id, false);
            throw problem(409, "STATE_CHANGED", "Retry completion is committed with the final answer");
        }
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
        if (sequence.isEmpty() || quizProgress.firstUnanswered(sequence, attempt.getQuizProgress()).isPresent())
            throw problem(409, "ATTEMPT_NOT_COMPLETED", "Answer every quiz correctly before completing");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        ContentMutationReceiptVo saved = ContentMutationReceiptVo.builder()
                .key(key)
                .fingerprint(fingerprint)
                .stage(null)
                .itemIndex(0)
                .revision(revision(attempt) + 1)
                .accessedAt(now)
                .roundedProgress(true)
                .build();
        UserTopicItemAttempt result = mutations.complete(
                attempt, completionResult(pinned, sequence, attempt.getQuizProgress()), now, saved);
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
        if (sequence.isEmpty() || quizProgress.firstUnanswered(sequence, progress).isPresent())
            throw problem(409, "ATTEMPT_NOT_COMPLETED", "Quiz results are incomplete");
        int firstTry = 0;
        for (TopicQuizMapper.QuizEntry quiz : sequence) {
            QuizProgressVo saved = progress.stream().filter(value -> value != null && quiz.quizId().equals(value.getQuizId())).findFirst().orElseThrow();
            if (saved.getSubmissions() != null && !saved.getSubmissions().isEmpty() && Boolean.TRUE.equals(saved.getSubmissions().get(0).getIsCorrect())) firstTry++;
        }
        return CompletionResultVo.builder()
                .title(pinned.getTitle())
                .firstTryCorrectCount(firstTry)
                .totalQuizCount(sequence.size())
                .build();
    }

    @Override
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
                retrySession = retrySessionMapper.toResponse(runtime);
                String nextId = retrySession.nextTopicItemId();
                next = nextId == null ? null : detail.getItems().stream().filter(item -> nextId.equals(item.getTopicItemId())).findFirst().orElse(null);
                boolean allowed = next != null && runtime.getStatus() == RetryStatus.IN_PROGRESS;
                availability = new TopicCatalogPolicy.Availability(allowed, allowed ? null : "CONTENT_UNAVAILABLE");
            }
        }
        return new TopicItemResultResponse(id, attempt.getTopicId(), attempt.getTopicItemId(), result.getTitle(),
                result.getFirstTryCorrectCount(), result.getTotalQuizCount(), accuracy(result), attempt.getCompletedAt(),
                next == null ? null : next.getTopicItemId(), availability, retrySession);
    }

    @Override
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

    @Override
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
            firstTry += result.getFirstTryCorrectCount(); total += result.getTotalQuizCount();
            results.add(new TopicResultResponse.Item(item.getTopicItemId(), item.getTitle(), attempt.getId(),
                    result.getFirstTryCorrectCount(), result.getTotalQuizCount(), accuracy(result), item.isHasNewContent(), item.getActions()));
        }
        return new TopicResultResponse(topicId, detail.getTitleVi(), firstTry, total, (int)Math.round(100.0 * firstTry / total), results, detail.getRetryAvailability(), detail.getResetAvailability());
    }

    private CompletionResultVo resultData(UserTopicItemAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.COMPLETED) throw problem(409, "RESULT_CHANGED", "The completed result is unavailable");
        if (attempt.getCompletionResult() != null) return attempt.getCompletionResult();
        UserTopicItemProgress legacy = mutations.findCompletedProgress(attempt);
        if (legacy == null || legacy.getFirstTryCorrectCount() == null || legacy.getTotalQuizCount() == null || legacy.getTotalQuizCount() < 1)
            throw problem(409, "RESULT_CHANGED", "Stored result counts are unavailable");
        return CompletionResultVo.builder()
                .title(version(attempt).getTitle())
                .firstTryCorrectCount(legacy.getFirstTryCorrectCount())
                .totalQuizCount(legacy.getTotalQuizCount())
                .build();
    }

    private int accuracy(CompletionResultVo result) { return (int)Math.round(100.0 * result.getFirstTryCorrectCount() / result.getTotalQuizCount()); }

    private void reconcileHiddenRunning(String userId, String topicId) {
        for (UserTopicItemAttempt attempt : attempts.findByUserIdAndTopicId(userId, topicId)) {
            if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) continue;
            try { requireItemVisible(attempt, version(attempt)); }
            catch (TopicRequestException unavailable) {
                if (unavailable.getStatus() != 410) throw unavailable;
                mutations.abandonHidden(attempt, revision(attempt) + 1);
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

    @Override
    public Map<String, Instant> access(String id, String key) {
        if (isRetry(id)) return retries.access(id, key);
        UserTopicItemAttempt attempt = owned(id);
        requireItemVisible(attempt, version(attempt));
        if (attempt.getStatus() == AttemptStatus.ABANDONED) throw problem(409, "STATE_CHANGED", "This attempt is no longer active");
        ContentMutationReceiptVo replay = receipt(attempt, key, "access");
        if (replay != null) return Map.of("lastAccessedAt", replay.getAccessedAt());
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        ContentMutationReceiptVo saved = ContentMutationReceiptVo.builder()
                .key(key)
                .fingerprint("access")
                .stage(null)
                .itemIndex(0)
                .revision(revision(attempt))
                .accessedAt(now)
                .roundedProgress(true)
                .build();
        UserTopicItemAttempt result = mutations.recordAccess(attempt, now, saved);
        if (result == null) throw problem(409, "STATE_CHANGED", "Attempt changed while recording access");
        requireGeneration(result);
        return Map.of("lastAccessedAt", now);
    }

    @Override
    public TopicAttemptResponse describe(UserTopicItemAttempt attempt) {
        requireGeneration(attempt);
        if (attempt.getAttemptType() == AttemptType.RETRY) {
            TopicItemVersion version = versions.findById(attempt.getTopicItemVersionId()).orElseThrow();
            return attemptMapper.toRetryResponse(attempt, version);
        }
        FurthestReachedVo position = attempt.getFurthestReached();
        return describe(attempt, position == null ? null : position.getStage(), position == null ? 0 : position.getItemIndex(), revision(attempt), attempt.getLastAccessedAt());
    }

    private TopicAttemptResponse describe(UserTopicItemAttempt attempt, LearningStage stage, int index, long revision, Instant accessed) {
        return describe(attempt, stage, index, revision, accessed, true);
    }

    private TopicAttemptResponse describeReceipt(UserTopicItemAttempt attempt, ContentMutationReceiptVo receipt) {
        return describe(attempt, receipt.getStage(), receipt.getItemIndex(), receipt.getRevision(), receipt.getAccessedAt(), Boolean.TRUE.equals(receipt.getRoundedProgress()));
    }

    private TopicAttemptResponse describe(UserTopicItemAttempt attempt, LearningStage stage, int index, long revision, Instant accessed, boolean rounded) {
        requireGeneration(attempt);
        TopicItemVersion version = version(attempt);
        TopicItemContentResponse content = TopicAttemptContentMapper.content(version);
        List<TopicCatalogPolicy.Position> positions = contentPositions(content);
        int totalQuiz = quizProgress.quizCount(version, QuizType.FILL_BLANK) + quizProgress.quizCount(version, QuizType.SITUATIONAL_CHOICE);
        int completed = stage == null && attempt.getStatus() == AttemptStatus.COMPLETED ? positions.size() + totalQuiz : positions.indexOf(new TopicCatalogPolicy.Position(stage == null ? "CONTEXT" : stage.name(), index));
        if (completed < 0) completed = positions.size() + (stage == LearningStage.SITUATIONAL_CHOICE ? quizProgress.quizCount(version, QuizType.FILL_BLANK) : 0) + index;
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

    @Override
    public TopicProgressResponse topicProgress(String topicId) {
        userId();
        TopicDetailResponse detail = catalog.detail(topicId);
        TopicCatalogPolicy.ProgressSummary summary = detail.getProgress();
        TopicProgressResponse response = TopicProgressResponse.builder().topicId(topicId)
                .status(ItemProgressStatus.valueOf(summary.status())).completedItemCount(summary.completedItemCount())
                .totalItemCount(summary.totalItemCount()).items(new ArrayList<>()).build();
        long generation = detail.getGeneration();
        List<UserTopicItemAttempt> saved = new ArrayList<>(attempts.findByUserIdAndTopicId(userId(), topicId).stream().filter(a -> a.getGeneration() == generation).toList());
        TopicRetryRuntime retryState = retries.runtime(topicId, generation);
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
            int quizzes = quizProgress.quizCount(pinned, QuizType.FILL_BLANK) + quizProgress.quizCount(pinned, QuizType.SITUATIONAL_CHOICE);
            totalWords += words; totalExpressions += expressions; totalQuiz += quizzes;
            if ("COMPLETED".equals(item.getLearningStatus())) {
                learnedWords += words; learnedExpressions += expressions; doneQuiz += quizzes;
            } else if (active != null) {
                TopicAttemptResponse.Progress progress = describe(active).progress();
                learnedWords += progress.learnedVocabularyCount(); learnedExpressions += progress.learnedExpressionCount(); doneQuiz += progress.completedQuizCount();
            }
            response.getItems().add(TopicItemProgressResponse.builder().topicItemId(item.getTopicItemId())
                    .status(ItemProgressStatus.valueOf(item.getLearningStatus())).completedAt(completedAttempt == null ? null : completedAttempt.getCompletedAt())
                    .firstTryCorrectCount(completedAttempt == null || completedAttempt.getCompletionResult() == null ? null : completedAttempt.getCompletionResult().getFirstTryCorrectCount())
                    .totalQuizCount(completedAttempt == null || completedAttempt.getCompletionResult() == null ? null : completedAttempt.getCompletionResult().getTotalQuizCount()).build());
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

    private ContentMutationReceiptVo receipt(UserTopicItemAttempt attempt, String key, String fingerprint) {
        if (key.equals(attempt.getStartRequestKey())) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for start");
        if (attempt.getMutationReceipts() != null) for (ContentMutationReceiptVo receipt : attempt.getMutationReceipts()) {
            if (receipt.getKey().equals(key)) {
                if (!receipt.getFingerprint().equals(fingerprint)) throw problem(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for another action");
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
        requireGeneration(attempt);
        return attempt;
    }

    private void requireGeneration(UserTopicItemAttempt attempt) {
        generations.require(attempt.getUserId(), attempt.getTopicId(), attempt.getGeneration());
    }

    private boolean isRetry(String id) { return retries.isRetry(id); }

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
