package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.response.CurrentQuizResponse;
import fit.iuh.engolearn.topic.dto.response.QuizFillBlankResponse;
import fit.iuh.engolearn.topic.exception.TopicRequestException;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper;
import fit.iuh.engolearn.topic.mapper.TopicQuizMapper.QuizEntry;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.AttemptType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.model.enums.QuizType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicQuizReadTest {

    private static final String ATTEMPT_ID = "a1";
    private static final String USER_ID = "u1";
    private static final Instant SUBMITTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static final List<QuizEntry> SEQUENCE = List.of(
            entry("iv1:quiz:0:0", QuizType.FILL_BLANK, 0, 4),
            entry("iv1:quiz:0:1", QuizType.FILL_BLANK, 1, 4),
            entry("iv1:quiz:0:2", QuizType.FILL_BLANK, 2, 4),
            entry("iv1:quiz:0:3", QuizType.FILL_BLANK, 3, 4),
            entry("iv1:quiz:1:0", QuizType.SITUATIONAL_CHOICE, 0, 2),
            entry("iv1:quiz:1:1", QuizType.SITUATIONAL_CHOICE, 1, 2));

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

    private TopicAttemptService service;

    @BeforeEach
    void setUp() {
        service = new TopicAttemptService(learner, catalog, attempts, versions, mongo, topicItems, learning, quizMapper,
                topics, releases);
    }

    @Test
    void withoutProgressReturnsFirstFillBlankQuestion() {
        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, List.of())).contains(SEQUENCE.get(0));
    }

    @Test
    void wrongAnswerKeepsTheSameQuestionCurrent() {
        List<QuizProgressVo> progress = List.of(progress("iv1:quiz:0:0", false));

        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, progress)).contains(SEQUENCE.get(0));
    }

    @Test
    void correctAnswerAdvancesToTheNextQuestion() {
        List<QuizProgressVo> progress = List.of(progress("iv1:quiz:0:0", true));

        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, progress)).contains(SEQUENCE.get(1));
    }

    @Test
    void correctFirstAndWrongSecondKeepsTheSecondQuestionCurrent() {
        List<QuizProgressVo> progress = List.of(
                progress("iv1:quiz:0:0", true),
                progress("iv1:quiz:0:1", false));

        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, progress)).contains(SEQUENCE.get(1));
    }

    @Test
    void allFillBlankCorrectReturnsFirstSituationalChoiceQuestion() {
        List<QuizProgressVo> progress = List.of(
                progress("iv1:quiz:0:0", true),
                progress("iv1:quiz:0:1", true),
                progress("iv1:quiz:0:2", true),
                progress("iv1:quiz:0:3", true));

        QuizEntry current = TopicAttemptService.firstUnanswered(SEQUENCE, progress).orElseThrow();

        assertThat(current).isEqualTo(SEQUENCE.get(4));
        assertThat(current.type()).isEqualTo(QuizType.SITUATIONAL_CHOICE);
        assertThat(current.indexInType()).isZero();
    }

    @Test
    void allQuestionsCorrectIsEmpty() {
        List<QuizProgressVo> progress = List.of(
                progress("iv1:quiz:0:0", true),
                progress("iv1:quiz:0:1", true),
                progress("iv1:quiz:0:2", true),
                progress("iv1:quiz:0:3", true),
                progress("iv1:quiz:1:0", true),
                progress("iv1:quiz:1:1", true));

        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, progress)).isEmpty();
    }

    @Test
    void unknownQuizIdInProgressIsIgnored() {
        List<QuizProgressVo> progress = List.of(progress("iv1:quiz:9:9", true));

        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, progress)).contains(SEQUENCE.get(0));
    }

    @Test
    void nullProgressReturnsFirstQuestion() {
        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, null)).contains(SEQUENCE.get(0));
    }

    @Test
    void duplicateQuizIdEntriesCountAsAnsweredWhenOneIsCorrect() {
        List<QuizProgressVo> progress = List.of(
                progress("iv1:quiz:0:0", false),
                progress("iv1:quiz:0:0", true));

        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, progress)).contains(SEQUENCE.get(1));
    }

    @Test
    void nullEntriesInTheSequenceAreSkipped() {
        List<QuizEntry> withNull = new ArrayList<>(SEQUENCE);
        withNull.add(0, null);

        assertThat(TopicAttemptService.firstUnanswered(withNull, List.of())).contains(SEQUENCE.get(0));
    }

    @Test
    void nullSubmissionsAndNullQuizIdsAreIgnored() {
        List<QuizProgressVo> progress = List.of(
                QuizProgressVo.builder().quizId("iv1:quiz:0:0").build(),
                QuizProgressVo.builder().quizId("iv1:quiz:0:0")
                        .submissions(Arrays.asList((QuizSubmissionVo) null)).build(),
                QuizProgressVo.builder().submissions(List.of(submission(true))).build());

        assertThat(TopicAttemptService.firstUnanswered(SEQUENCE, progress)).contains(SEQUENCE.get(0));
    }

    @Test
    void missingAttemptIs404BeforeAnyQuizRead() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.empty());

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.getCode()).isEqualTo("CONTENT_UNAVAILABLE");
        verifyNoInteractions(versions, quizMapper, mongo, catalog, learning, topicItems);
    }

    @Test
    void completedAttemptReturnsNullQuizWithoutReadingQuizSequence() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(
                Optional.of(attempt(AttemptStatus.COMPLETED, null)));
        when(versions.findById("iv1")).thenReturn(Optional.of(itemVersion(
                fillBlankModule(0), situationalModule(1))));

        CurrentQuizResponse response = service.getCurrentQuiz(ATTEMPT_ID);

        assertThat(response.attempt().attemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(response.attempt().status()).isEqualTo("COMPLETED");
        assertThat(response.attempt().position()).isNull();
        assertThat(response.quiz()).isNull();
        verify(quizMapper, never()).sequence(any());
        verifyNoInteractions(mongo, catalog, learning, topicItems);
    }

    @Test
    void allQuestionsCorrectReturnsNullQuizWhileStillInProgress() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of(
                progress("iv1:quiz:0:0", true),
                progress("iv1:quiz:0:1", true),
                progress("iv1:quiz:0:2", true),
                progress("iv1:quiz:0:3", true),
                progress("iv1:quiz:1:0", true),
                progress("iv1:quiz:1:1", true)))));
        when(versions.findById("iv1")).thenReturn(Optional.of(itemVersion(
                fillBlankModule(0), situationalModule(1))));
        visibleItemInLatestRelease();

        CurrentQuizResponse response = service.getCurrentQuiz(ATTEMPT_ID);

        assertThat(response.quiz()).isNull();
        assertThat(response.attempt().status()).isEqualTo("IN_PROGRESS");
        assertThat(response.attempt().completedAt()).isNull();
        verify(quizMapper, never()).toDto(any());
        verifyNoInteractions(mongo, catalog, learning, topicItems);
    }

    @Test
    void inProgressAttemptReturnsTheFirstUnansweredQuizAsDto() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(
                AttemptStatus.IN_PROGRESS, List.of(progress("iv1:quiz:0:0", true)))));
        when(versions.findById("iv1")).thenReturn(Optional.of(itemVersion(fillBlankModule(0))));
        visibleItemInLatestRelease();

        CurrentQuizResponse response = service.getCurrentQuiz(ATTEMPT_ID);

        assertThat(response.quiz()).isInstanceOf(QuizFillBlankResponse.class);
        QuizFillBlankResponse quiz = (QuizFillBlankResponse) response.quiz();
        assertThat(quiz.quizId()).isEqualTo("iv1:quiz:0:1");
        assertThat(quiz.index()).isEqualTo(1);
        assertThat(quiz.totalInType()).isEqualTo(2);
        assertThat(response.attempt().status()).isEqualTo("IN_PROGRESS");
        verifyNoInteractions(mongo, catalog, learning, topicItems);
    }

    @Test
    void hiddenPinnedVersionIs410WithoutReadingTopic() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of())));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(ContentStatus.HIDDEN, fillBlankModule(0))));

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verifyNoInteractions(topics, releases, quizMapper);
    }

    @Test
    void missingTopicIs410() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of())));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(null, fillBlankModule(0))));
        when(topics.findById("t1")).thenReturn(Optional.empty());

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verifyNoInteractions(releases, quizMapper);
    }

    @Test
    void blankLatestPublishedVersionIs410() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of())));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(null, fillBlankModule(0))));
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("  ").build()));

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verifyNoInteractions(releases, quizMapper);
    }

    @Test
    void missingReleaseIs410() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of())));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(null, fillBlankModule(0))));
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("tv1").build()));
        when(releases.findById("tv1")).thenReturn(Optional.empty());

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verify(versions, never()).findAllById(any());
        verifyNoInteractions(quizMapper);
    }

    @Test
    void draftReleaseIs410() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of())));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(null, fillBlankModule(0))));
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("tv1").build()));
        when(releases.findById("tv1")).thenReturn(Optional.of(TopicVersion.builder()
                .status(ContentStatus.DRAFT).itemVersionIds(List.of("iv1")).build()));

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verify(versions, never()).findAllById(any());
        verifyNoInteractions(quizMapper);
    }

    @Test
    void itemMissingFromLatestReleaseIs410() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of())));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(null, fillBlankModule(0))));
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("tv1").build()));
        when(releases.findById("tv1")).thenReturn(Optional.of(TopicVersion.builder()
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of("iv9")).build()));
        when(versions.findAllById(List.of("iv9"))).thenReturn(List.of(releaseItem("iv9", "i9", ContentStatus.PUBLISHED)));

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verifyNoInteractions(quizMapper);
    }

    @Test
    void hiddenNewestVersionOfItemInLatestReleaseIs410() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.IN_PROGRESS, List.of())));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(null, fillBlankModule(0))));
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("tv1").build()));
        when(releases.findById("tv1")).thenReturn(Optional.of(TopicVersion.builder()
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of("iv2")).build()));
        when(versions.findAllById(List.of("iv2"))).thenReturn(List.of(releaseItem("iv2", "i1", ContentStatus.HIDDEN)));

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(410);
        assertThat(error.getCode()).isEqualTo("ITEM_HIDDEN");
        verifyNoInteractions(quizMapper);
    }

    @Test
    void newerVisibleVersionOfSameItemDoesNotBlockTheAttempt() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(
                AttemptStatus.IN_PROGRESS, List.of(progress("iv1:quiz:0:0", true)))));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(null, fillBlankModule(0))));
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("tv1").build()));
        when(releases.findById("tv1")).thenReturn(Optional.of(TopicVersion.builder()
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of("iv2")).build()));
        when(versions.findAllById(List.of("iv2"))).thenReturn(List.of(releaseItem("iv2", "i1", ContentStatus.PUBLISHED)));

        CurrentQuizResponse response = service.getCurrentQuiz(ATTEMPT_ID);

        assertThat(response.quiz()).isInstanceOf(QuizFillBlankResponse.class);
        QuizFillBlankResponse quiz = (QuizFillBlankResponse) response.quiz();
        assertThat(quiz.quizId()).isEqualTo("iv1:quiz:0:1");
        assertThat(quiz.index()).isEqualTo(1);
        assertThat(response.attempt().status()).isEqualTo("IN_PROGRESS");
        verifyNoInteractions(mongo, catalog, learning, topicItems);
    }

    @Test
    void completedAttemptWithHiddenPinnedVersionStillReturnsNullQuiz() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt(AttemptStatus.COMPLETED, null)));
        when(versions.findById("iv1")).thenReturn(Optional.of(pinnedVersion(ContentStatus.HIDDEN, fillBlankModule(0))));

        CurrentQuizResponse response = service.getCurrentQuiz(ATTEMPT_ID);

        assertThat(response.quiz()).isNull();
        assertThat(response.attempt().status()).isEqualTo("COMPLETED");
        verifyNoInteractions(topics, releases);
    }

    @Test
    void foreignAttemptIs404WithoutVisibilityRead() {
        when(learner.getCurrentUserId()).thenReturn(USER_ID);
        UserTopicItemAttempt foreign = attempt(AttemptStatus.IN_PROGRESS, List.of());
        foreign.setUserId("u2");
        when(attempts.findById(ATTEMPT_ID)).thenReturn(Optional.of(foreign));

        TopicRequestException error = catchThrowableOfType(
                () -> service.getCurrentQuiz(ATTEMPT_ID), TopicRequestException.class);

        assertThat(error.getStatus()).isEqualTo(404);
        assertThat(error.getCode()).isEqualTo("CONTENT_UNAVAILABLE");
        verifyNoInteractions(topics, releases, versions, quizMapper);
    }

    private void visibleItemInLatestRelease() {
        when(topics.findById("t1")).thenReturn(Optional.of(Topic.builder().latestPublishedVersion("tv1").build()));
        when(releases.findById("tv1")).thenReturn(Optional.of(TopicVersion.builder()
                .status(ContentStatus.PUBLISHED).itemVersionIds(List.of("iv1")).build()));
        when(versions.findAllById(List.of("iv1"))).thenReturn(List.of(releaseItem("iv-live", "i1", ContentStatus.PUBLISHED)));
    }

    private static QuizEntry entry(String quizId, QuizType type, int index, int total) {
        return new QuizEntry(quizId, type, index, total, null, null, 0);
    }

    private static QuizProgressVo progress(String quizId, boolean... results) {
        List<QuizSubmissionVo> submissions = new ArrayList<>();
        for (boolean result : results) submissions.add(submission(result));
        return QuizProgressVo.builder().quizId(quizId).submissions(submissions).build();
    }

    private static QuizSubmissionVo submission(boolean correct) {
        return QuizSubmissionVo.builder().answer("option").isCorrect(correct).submittedAt(SUBMITTED_AT).build();
    }

    private static UserTopicItemAttempt attempt(AttemptStatus status, List<QuizProgressVo> quizProgress) {
        return UserTopicItemAttempt.builder()
                .id(ATTEMPT_ID).userId(USER_ID).topicId("t1").topicItemId("i1").topicItemVersionId("iv1")
                .attemptType(AttemptType.LEARNING)
                .status(status)
                .furthestReached(status == AttemptStatus.IN_PROGRESS
                        ? FurthestReachedVo.builder().stage(LearningStage.FILL_BLANK).itemIndex(0).build() : null)
                .quizProgress(quizProgress)
                .version(3L)
                .startedAt(SUBMITTED_AT).lastAccessedAt(SUBMITTED_AT)
                .build();
    }

    private static TopicItemVersion itemVersion(QuizModuleVo... modules) {
        return pinnedVersion(null, modules);
    }

    private static TopicItemVersion pinnedVersion(ContentStatus status, QuizModuleVo... modules) {
        return TopicItemVersion.builder()
                .id("iv1").version(1).topicItemId("i1").status(status)
                .context(TopicContextVo.builder().scenarioDescription("At the counter").build())
                .vocabularies(List.of())
                .expressions(List.of())
                .quizzes(List.of(modules))
                .build();
    }

    private static TopicItemVersion releaseItem(String id, String topicItemId, ContentStatus status) {
        return TopicItemVersion.builder().id(id).version(2).topicItemId(topicItemId).status(status).build();
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
