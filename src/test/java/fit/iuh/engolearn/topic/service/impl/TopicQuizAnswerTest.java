package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.infra.dev.LocalLearnerProvider;
import fit.iuh.engolearn.topic.service.*;

import fit.iuh.engolearn.topic.dto.request.AnswerQuizRequest;
import fit.iuh.engolearn.topic.dto.response.QuizAnswerResponse;
import fit.iuh.engolearn.topic.dto.response.TopicCatalogPolicy;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
import fit.iuh.engolearn.topic.mapper.TopicRetrySessionMapper;
import fit.iuh.engolearn.topic.repository.TopicAttemptMutationRepository;
import fit.iuh.engolearn.topic.mapper.TopicAttemptMapper;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.AttemptType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.model.enums.QuizType;
import fit.iuh.engolearn.topic.model.vo.ContentMutationReceiptVo;
import fit.iuh.engolearn.topic.model.vo.FurthestReachedVo;
import fit.iuh.engolearn.topic.model.vo.QuizModuleVo;
import fit.iuh.engolearn.topic.model.vo.QuizOptionVo;
import fit.iuh.engolearn.topic.model.vo.QuizProgressVo;
import fit.iuh.engolearn.topic.model.vo.QuizQuestionVo;
import fit.iuh.engolearn.topic.model.vo.QuizSentenceVo;
import fit.iuh.engolearn.topic.model.vo.QuizSubmissionVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemAttemptRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicQuizAnswerTest {
    @org.mockito.Mock
    private TopicAttemptMapper attemptMapper;

    @org.mockito.Mock
    private TopicRetryService retries;

    @org.mockito.Mock
    private TopicGenerationService generations;


    private static final String ATTEMPT_ID = "a1";
    private static final String USER_ID = "u1";
    private static final String KEY = "key-1";
    private static final Instant LOADED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-01-01T00:00:01Z");

    private static final String FIRST = "iv1:quiz:0:0";
    private static final String SECOND = "iv1:quiz:0:1";
    private static final String FIRST_SITUATIONAL = "iv1:quiz:1:0";
    private static final String LAST = "iv1:quiz:1:1";
    private static final String FIRST_CORRECT = FIRST + ":option:1";
    private static final String FIRST_WRONG = FIRST + ":option:0";
    private static final String SECOND_CORRECT = SECOND + ":option:1";
    private static final String FIRST_SITUATIONAL_CORRECT = FIRST_SITUATIONAL + ":option:2";
    private static final String LAST_CORRECT = LAST + ":option:2";

    @Mock
    private LocalLearnerProvider learner;

    @Mock
    private TopicLearnerCatalogService catalog;

    @Mock
    private UserTopicItemAttemptRepository attempts;

    @Mock
    private TopicItemVersionRepository versions;

    @Mock
    private TopicRepository topics;

    @Mock
    private TopicVersionRepository releases;

    @Mock
    private MongoTemplate mongo;

    @Mock
    private TopicItemRepository topicItems;

    @Mock
    private TopicLearningService learning;

    @Spy
    private TopicQuizMapper quizMapper = new TopicQuizMapper();

    private final TopicQuizProgressService quizProgress = new TopicQuizProgressServiceImpl();

    private TopicAttemptService service;

    @BeforeEach
    void setUp() {
        service = new TopicAttemptServiceImpl(learner, catalog, attempts, versions, new TopicAttemptMutationRepository(mongo), topicItems, learning, quizMapper,
                topics, releases, generations, retries, attemptMapper, quizProgress, new TopicRetrySessionMapper());
    }

    @Test
    void correctFirstQuestionCommitsSubmissionFeedbackAndNextFrontier() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class))).thenReturn(committed(LearningStage.FILL_BLANK, 1));

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY);

        assertThat(response.quizId()).isEqualTo(FIRST);
        assertThat(response.isCorrect()).isTrue();
        assertThat(response.feedback()).isNotNull();
        assertThat(response.feedback().correctOptionId()).isEqualTo(FIRST_CORRECT);
        assertThat(response.feedback().explanationVi()).isEqualTo("Choose the verb that fits");
        assertThat(response.attempt().revision()).isEqualTo(4L);
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("FILL_BLANK", 1));
        assertThat(response.attempt().status()).isEqualTo("IN_PROGRESS");
        assertThat(response.attempt().completedAt()).isNull();

        Update update = singleCommit();
        Document set = setOf(update);
        assertThat(set.keySet()).containsExactlyInAnyOrder("quiz_progress", "version", "last_accessed_at", "furthest_reached");
        assertThat(set.get("version")).isEqualTo(4L);
        FurthestReachedVo frontier = (FurthestReachedVo) set.get("furthest_reached");
        assertThat(frontier.getStage()).isEqualTo(LearningStage.FILL_BLANK);
        assertThat(frontier.getItemIndex()).isEqualTo(1);

        List<QuizProgressVo> stored = storedProgress(set);
        assertThat(stored).hasSize(1);
        QuizProgressVo state = stored.get(0);
        assertThat(state.getQuizId()).isEqualTo(FIRST);
        assertThat(state.getCompletedAt()).isNotNull();
        assertThat(state.getSubmissions()).hasSize(1);
        assertThat(state.getSubmissions().get(0).getAnswer()).isEqualTo(FIRST_CORRECT);
        assertThat(state.getSubmissions().get(0).getIsCorrect()).isTrue();
        assertThat(state.getSubmissions().get(0).getSubmittedAt()).isNotNull();

        ContentMutationReceiptVo pushed = receiptOf(pushOf(update));
        assertThat(pushed.getKey()).isEqualTo(KEY);
        assertThat(pushed.getFingerprint()).isEqualTo(answerFingerprint(3L));
        assertThat(pushed.getRevision()).isEqualTo(4L);
        assertThat(pushed.getStage()).isEqualTo(LearningStage.FILL_BLANK);
        assertThat(pushed.getItemIndex()).isEqualTo(1);
    }

    @Test
    void wrongAnswerKeepsFrontierButIncrementsRevision() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class))).thenReturn(committed(LearningStage.FILL_BLANK, 0));

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_WRONG), KEY);

        assertThat(response.isCorrect()).isFalse();
        assertThat(response.feedback()).isNull();
        assertThat(response.attempt().revision()).isEqualTo(4L);
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("FILL_BLANK", 0));

        Update update = singleCommit();
        Document set = setOf(update);
        assertThat(set.keySet()).containsExactlyInAnyOrder("quiz_progress", "version", "last_accessed_at");
        assertThat(set.get("version")).isEqualTo(4L);
        assertThat(update.modifies("furthest_reached")).isFalse();

        QuizProgressVo state = storedProgress(set).get(0);
        assertThat(state.getCompletedAt()).isNull();
        assertThat(state.getSubmissions()).hasSize(1);
        assertThat(state.getSubmissions().get(0).getIsCorrect()).isFalse();
        assertThat(state.getSubmissions().get(0).getAnswer()).isEqualTo(FIRST_WRONG);
        ContentMutationReceiptVo pushed = receiptOf(pushOf(update));
        assertThat(pushed.getKey()).isEqualTo(KEY);
        assertThat(pushed.getStage()).isEqualTo(LearningStage.FILL_BLANK);
        assertThat(pushed.getItemIndex()).isZero();
        assertThat(pushed.getRevision()).isEqualTo(4L);
    }

    @Test
    void wrongThenCorrectAppendsSecondSubmissionWithoutTouchingTheLoadedAttempt() {
        List<QuizSubmissionVo> loadedSubmissions = new ArrayList<>(List.of(submission(FIRST_WRONG, false)));
        QuizProgressVo loadedState = QuizProgressVo.builder().quizId(FIRST).submissions(loadedSubmissions).build();
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0,
                new ArrayList<>(List.of(loadedState))));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class))).thenReturn(committed(LearningStage.FILL_BLANK, 1));

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY);

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.feedback()).isNotNull();
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("FILL_BLANK", 1));

        List<QuizProgressVo> stored = storedProgress(setOf(singleCommit()));
        assertThat(stored).hasSize(1);
        List<QuizSubmissionVo> storedSubmissions = stored.get(0).getSubmissions();
        assertThat(storedSubmissions).hasSize(2);
        assertThat(storedSubmissions.get(0).getIsCorrect()).isFalse();
        assertThat(storedSubmissions.get(0).getAnswer()).isEqualTo(FIRST_WRONG);
        assertThat(storedSubmissions.get(1).getIsCorrect()).isTrue();
        assertThat(storedSubmissions.get(1).getAnswer()).isEqualTo(FIRST_CORRECT);
        assertThat(stored.get(0).getCompletedAt()).isNotNull();

        assertThat(loadedSubmissions).hasSize(1);
        assertThat(loadedState.getCompletedAt()).isNull();
    }

    @Test
    void correctLastQuestionKeepsFrontierAndDoesNotCompleteTheAttempt() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.SITUATIONAL_CHOICE, 1, List.of(
                answered(FIRST), answered(SECOND), answered(FIRST_SITUATIONAL))));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class))).thenReturn(committed(LearningStage.SITUATIONAL_CHOICE, 1));

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, LAST, body(3L, LAST_CORRECT), KEY);

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.feedback()).isNotNull();
        assertThat(response.feedback().correctOptionId()).isEqualTo(LAST_CORRECT);
        assertThat(response.attempt().status()).isEqualTo("IN_PROGRESS");
        assertThat(response.attempt().completedAt()).isNull();
        assertThat(response.attempt().revision()).isEqualTo(4L);
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("SITUATIONAL_CHOICE", 1));

        Update update = singleCommit();
        Document set = setOf(update);
        assertThat(set.keySet()).containsExactlyInAnyOrder("quiz_progress", "version", "last_accessed_at");
        assertThat(update.modifies("furthest_reached")).isFalse();
        assertThat(update.modifies("status")).isFalse();
        assertThat(update.modifies("completed_at")).isFalse();

        List<QuizProgressVo> stored = storedProgress(set);
        assertThat(stored).hasSize(4);
        QuizProgressVo last = stored.get(3);
        assertThat(last.getQuizId()).isEqualTo(LAST);
        assertThat(last.getCompletedAt()).isNotNull();
        assertThat(last.getSubmissions()).hasSize(1);
        assertThat(last.getSubmissions().get(0).getIsCorrect()).isTrue();
    }

    @Test
    void correctLastFillBlankAdvancesToSituationalChoice() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of(answered(FIRST))));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class))).thenReturn(committed(LearningStage.SITUATIONAL_CHOICE, 0));

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, SECOND, body(3L, SECOND_CORRECT), KEY);

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("SITUATIONAL_CHOICE", 0));

        Document set = setOf(singleCommit());
        assertThat(set.keySet()).containsExactlyInAnyOrder("quiz_progress", "version", "last_accessed_at", "furthest_reached");
        FurthestReachedVo frontier = (FurthestReachedVo) set.get("furthest_reached");
        assertThat(frontier.getStage()).isEqualTo(LearningStage.SITUATIONAL_CHOICE);
        assertThat(frontier.getItemIndex()).isZero();
    }

    @Test
    void staleRevisionIsRejectedBeforeAnyWrite() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(2L, FIRST_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("STATE_CHANGED");
        assertNoWrite();
    }

    @Test
    void answerBeforeQuizFrontierIs409WithoutAnyWrite() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.CONTEXT, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("STATE_CHANGED");
        verify(quizMapper, never()).sequence(any());
        assertNoWrite();
    }

    @Test
    void replayBeforeQuizFrontierStillReturnsTheCommittedAnswer() {
        UserTopicItemAttempt attempt = attempt(AttemptStatus.IN_PROGRESS, LearningStage.CONTEXT, 0,
                new ArrayList<>(List.of(QuizProgressVo.builder().quizId(FIRST)
                        .submissions(new ArrayList<>(List.of(submission(FIRST_CORRECT, true))))
                        .completedAt(SAVED_AT).build())));
        attempt.setVersion(5L);
        attempt.setLastAccessedAt(SAVED_AT);
        attempt.setMutationReceipts(new ArrayList<>(List.of(
                ContentMutationReceiptVo.builder()
                        .key(KEY)
                        .fingerprint(answerFingerprint(3L))
                        .stage(LearningStage.FILL_BLANK)
                        .itemIndex(1)
                        .revision(4L)
                        .accessedAt(SAVED_AT)
                        .build())));
        givenAttempt(attempt);
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY);

        assertThat(response.quizId()).isEqualTo(FIRST);
        assertThat(response.isCorrect()).isTrue();
        assertThat(response.feedback()).isNotNull();
        assertThat(response.attempt().revision()).isEqualTo(4L);
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("FILL_BLANK", 1));

        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class));
        verifyNoInteractions(topics, releases);
        assertNoRepositoryWrite();
        assertThat(attempt.getMutationReceipts()).hasSize(1);
    }

    @Test
    void replaySameKeyAndFingerprintSkipsRevisionAndVisibilityChecks() {
        UserTopicItemAttempt attempt = attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0,
                new ArrayList<>(List.of(QuizProgressVo.builder().quizId(FIRST)
                        .submissions(new ArrayList<>(List.of(submission(FIRST_CORRECT, true))))
                        .completedAt(SAVED_AT).build())));
        attempt.setVersion(5L);
        attempt.setLastAccessedAt(SAVED_AT);
        attempt.setMutationReceipts(new ArrayList<>(List.of(
                ContentMutationReceiptVo.builder()
                        .key(KEY)
                        .fingerprint(answerFingerprint(3L))
                        .stage(LearningStage.FILL_BLANK)
                        .itemIndex(1)
                        .revision(4L)
                        .accessedAt(SAVED_AT)
                        .build())));
        givenAttempt(attempt);
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY);

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.feedback()).isNotNull();
        assertThat(response.feedback().correctOptionId()).isEqualTo(FIRST_CORRECT);
        assertThat(response.attempt().revision()).isEqualTo(4L);
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("FILL_BLANK", 1));
        assertThat(response.attempt().lastAccessedAt()).isEqualTo(SAVED_AT);

        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class));
        verify(attempts, times(1)).findById(ATTEMPT_ID);
        verifyNoInteractions(topics, releases);
        assertNoRepositoryWrite();
        assertThat(attempt.getVersion()).isEqualTo(5L);
        assertThat(attempt.getLastAccessedAt()).isEqualTo(SAVED_AT);
        assertThat(attempt.getMutationReceipts()).hasSize(1);
    }

    @Test
    void replaySameKeyDifferentPayloadIsRejected() {
        UserTopicItemAttempt attempt = attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of());
        attempt.setMutationReceipts(new ArrayList<>(List.of(
                ContentMutationReceiptVo.builder()
                        .key(KEY)
                        .fingerprint(answerFingerprint(9L))
                        .stage(LearningStage.FILL_BLANK)
                        .itemIndex(1)
                        .revision(9L)
                        .accessedAt(SAVED_AT)
                        .build())));
        givenAttempt(attempt);

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verifyNoInteractions(versions, mongo, topics, releases, quizMapper);
        assertNoRepositoryWrite();
    }

    @Test
    void missingOrOversizedIdempotencyKeyIs400() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));

        TopicRequestException missing = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), null), TopicRequestException.class);
        TopicRequestException blank = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), "  "), TopicRequestException.class);
        TopicRequestException oversized = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), "k".repeat(129)),
                TopicRequestException.class);

        assertThat(missing.getStatus()).isEqualTo(400);
        assertThat(missing.getCode()).isEqualTo("INVALID_REQUEST");
        assertThat(blank.getStatus()).isEqualTo(400);
        assertThat(oversized.getStatus()).isEqualTo(400);
        verifyNoInteractions(versions, mongo, topics, releases);
        assertNoRepositoryWrite();
    }

    @Test
    void foreignAttemptIs404WithoutAnyContentRead() {
        UserTopicItemAttempt foreign = attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of());
        foreign.setUserId("u2");
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(foreign));

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.getCode()).isEqualTo("CONTENT_UNAVAILABLE");
        verifyNoInteractions(versions, mongo, topics, releases, quizMapper);
        assertNoRepositoryWrite();
    }

    @Test
    void unknownQuizIs404() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, "iv1:quiz:9:9", body(3L, "iv1:quiz:9:9:option:0"), KEY),
                TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.getCode()).isEqualTo("CONTENT_UNAVAILABLE");
        assertNoWrite();
    }

    @Test
    void quizThatIsNotTheCurrentOneIs409() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, SECOND, body(3L, SECOND_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("STATE_CHANGED");
        assertNoWrite();
    }

    @Test
    void malformedOptionIdIs400WithoutAnyWrite() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        TopicRequestException unknownFormat = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, "not-an-option"), KEY), TopicRequestException.class);
        TopicRequestException nonNumeric = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST + ":option:x"), KEY), TopicRequestException.class);
        TopicRequestException wrongQuizPrefix = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, SECOND + ":option:1"), KEY), TopicRequestException.class);

        assertThat(unknownFormat.getStatus()).isEqualTo(400);
        assertThat(unknownFormat.getCode()).isEqualTo("INVALID_REQUEST");
        assertThat(nonNumeric.getStatus()).isEqualTo(400);
        assertThat(wrongQuizPrefix.getStatus()).isEqualTo(400);
        assertNoWrite();
    }

    @Test
    void optionIdOutsideThePinnedOptionsIs400WithoutAnyWrite() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST + ":option:4"), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getCode()).isEqualTo("INVALID_REQUEST");
        assertNoWrite();
    }

    @Test
    void hiddenItemIs410BeforeAnyWrite() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(
                Optional.of(pinned(ContentStatus.HIDDEN, fillBlankModule(0), situationalModule(1))));

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verifyNoInteractions(topics, releases, mongo, quizMapper);
        assertNoRepositoryWrite();
    }

    @Test
    void completedAttemptIs409BeforeAnyWrite() {
        givenAttempt(attempt(AttemptStatus.COMPLETED, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("STATE_CHANGED");
        verifyNoInteractions(topics, releases, mongo, quizMapper);
        assertNoRepositoryWrite();
    }

    @Test
    void lostCasWithAConcurrentReceiptReplaysTheCommittedAnswer() {
        UserTopicItemAttempt stale = attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of());
        UserTopicItemAttempt latest = attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 1,
                new ArrayList<>(List.of(QuizProgressVo.builder().quizId(FIRST)
                        .submissions(new ArrayList<>(List.of(submission(FIRST_CORRECT, true))))
                        .completedAt(SAVED_AT).build())));
        latest.setVersion(4L);
        latest.setLastAccessedAt(SAVED_AT);
        latest.setMutationReceipts(new ArrayList<>(List.of(
                ContentMutationReceiptVo.builder()
                        .key(KEY)
                        .fingerprint(answerFingerprint(3L))
                        .stage(LearningStage.FILL_BLANK)
                        .itemIndex(1)
                        .revision(4L)
                        .accessedAt(SAVED_AT)
                        .build())));
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(stale), Optional.of(latest));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        QuizAnswerResponse response = service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY);

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.feedback()).isNotNull();
        assertThat(response.attempt().revision()).isEqualTo(4L);
        assertThat(response.attempt().position()).isEqualTo(new TopicCatalogPolicy.Position("FILL_BLANK", 1));
        assertThat(response.attempt().lastAccessedAt()).isEqualTo(SAVED_AT);

        verify(mongo, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class));
        verify(attempts, times(2)).findById(ATTEMPT_ID);
        assertNoRepositoryWrite();
    }

    @Test
    void lostCasWithoutAReceiptIs409() {
        givenAttempt(attempt(AttemptStatus.IN_PROGRESS, LearningStage.FILL_BLANK, 0, List.of()));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinned()));
        givenVisibleItem();

        TopicRequestException error = catchThrowableOfType(
                () -> service.answerQuiz(ATTEMPT_ID, FIRST, body(3L, FIRST_CORRECT), KEY), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo("STATE_CHANGED");
        verify(mongo, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class));
        verify(attempts, times(2)).findById(ATTEMPT_ID);
        assertNoRepositoryWrite();
    }

    private void givenAttempt(UserTopicItemAttempt attempt) {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
    }

    private void givenVisibleItem() {
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("tv1").build()));
        when(releases.findById("tv1")).thenReturn(Optional.of(TopicVersion.builder().status(ContentStatus.PUBLISHED)
                .itemVersionIds(List.of("iv1")).build()));
        when(versions.findAllById(List.of("iv1")))
                .thenReturn(List.of(TopicItemVersion.builder().id("iv-live").version(2).topicItemId("i1")
                        .status(ContentStatus.PUBLISHED).build()));
    }

    private Update singleCommit() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(1)).findAndModify(any(Query.class), captor.capture(), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class));
        assertNoRepositoryWrite();
        return captor.getValue();
    }

    private void assertNoWrite() {
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserTopicItemAttempt.class));
        assertNoRepositoryWrite();
    }

    private void assertNoRepositoryWrite() {
        verify(attempts, never()).save(any(UserTopicItemAttempt.class));
        verify(attempts, never()).insert(any(UserTopicItemAttempt.class));
    }

    private static Document setOf(Update update) {
        return update.getUpdateObject().get("$set", Document.class);
    }

    private static Document pushOf(Update update) {
        return update.getUpdateObject().get("$push", Document.class);
    }

    @SuppressWarnings("unchecked")
    private static List<QuizProgressVo> storedProgress(Document set) {
        return (List<QuizProgressVo>) set.get("quiz_progress");
    }

    private static ContentMutationReceiptVo receiptOf(Document push) {
        Object stored = push.get("mutation_receipts");
        assertThat(stored).isInstanceOf(ContentMutationReceiptVo.class);
        return (ContentMutationReceiptVo) stored;
    }

    private static AnswerQuizRequest body(long revision, String optionId) {
        return new AnswerQuizRequest(revision, optionId);
    }

    private static String answerFingerprint(long revision) {
        return "answer|" + revision + "|" + FIRST + "|" + FIRST_CORRECT;
    }

    private static UserTopicItemAttempt attempt(AttemptStatus status, LearningStage stage, int index,
            List<QuizProgressVo> progress) {
        return UserTopicItemAttempt.builder()
                .id(ATTEMPT_ID).userId(USER_ID).topicId("t1").topicItemId("i1").topicItemVersionId("iv1")
                .attemptType(AttemptType.LEARNING)
                .status(status)
                .furthestReached(stage == null ? null : FurthestReachedVo.builder().stage(stage).itemIndex(index).build())
                .quizProgress(progress)
                .version(3L)
                .startedAt(LOADED_AT).lastAccessedAt(LOADED_AT)
                .build();
    }

    private static UserTopicItemAttempt committed(LearningStage stage, int index) {
        UserTopicItemAttempt after = attempt(AttemptStatus.IN_PROGRESS, stage, index, List.of());
        after.setVersion(4L);
        after.setLastAccessedAt(SAVED_AT);
        return after;
    }

    private static QuizProgressVo answered(String quizId) {
        return QuizProgressVo.builder().quizId(quizId)
                .submissions(new ArrayList<>(List.of(submission("stored", true))))
                .completedAt(LOADED_AT)
                .build();
    }

    private static QuizSubmissionVo submission(String answer, boolean correct) {
        return QuizSubmissionVo.builder().answer(answer).isCorrect(correct).submittedAt(LOADED_AT).build();
    }

    private static TopicItemVersion pinned() {
        return pinned(null, fillBlankModule(0), situationalModule(1));
    }

    private static TopicItemVersion pinned(ContentStatus status, QuizModuleVo... modules) {
        return TopicItemVersion.builder()
                .id("iv1").version(1).topicItemId("i1").status(status)
                .context(TopicContextVo.builder().scenarioDescription("At the counter").build())
                .vocabularies(List.of())
                .expressions(List.of())
                .quizzes(List.of(modules))
                .build();
    }

    private static QuizModuleVo fillBlankModule(Integer orderIndex) {
        return QuizModuleVo.builder()
                .quizType(QuizType.FILL_BLANK)
                .orderIndex(orderIndex)
                .title("Fill blank")
                .questions(List.of(
                        fillBlankQuestion("I would like to reserve a ticket today.",
                                option("d", "buy", false), option("c", "reserve", true),
                                option("b", "take", false), option("a", "make", false)),
                        fillBlankQuestion("The train leaves at six o'clock.",
                                option("d", "comes", false), option("c", "leaves", true),
                                option("b", "goes", false), option("a", "arrives", false))))
                .build();
    }

    private static QuizModuleVo situationalModule(Integer orderIndex) {
        return QuizModuleVo.builder()
                .quizType(QuizType.SITUATIONAL_CHOICE)
                .orderIndex(orderIndex)
                .title("Situational")
                .questions(List.of(
                        situationalQuestion("Which sentence is polite?"),
                        situationalQuestion("How do you ask for help?")))
                .build();
    }

    private static QuizQuestionVo fillBlankQuestion(String sentence, QuizOptionVo... options) {
        return QuizQuestionVo.builder()
                .prompt("Fill in the blank")
                .audioUrl("question.mp3")
                .tips("Read the whole sentence")
                .explanation("Choose the verb that fits")
                .options(List.of(options))
                .sentence(QuizSentenceVo.builder().textEn(sentence).meaningVi("Nghĩa tiếng Việt").build())
                .build();
    }

    private static QuizQuestionVo situationalQuestion(String prompt) {
        return QuizQuestionVo.builder()
                .prompt(prompt)
                .explanation("Choose the polite request")
                .options(List.of(
                        option("A", "Could I have a map", false),
                        option("B", "How much is the ticket", false),
                        option("C", "Where is the departure gate", true),
                        option("D", "May I sit here", false)))
                .build();
    }

    private static QuizOptionVo option(String label, String text, boolean correct) {
        return QuizOptionVo.builder().label(label).textEn(text).isCorrect(correct).build();
    }
}
