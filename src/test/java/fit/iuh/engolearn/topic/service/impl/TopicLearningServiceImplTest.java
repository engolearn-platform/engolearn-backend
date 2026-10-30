package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.service.*;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.request.GetTopicItemContentRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicProgressRequest;
import fit.iuh.engolearn.topic.dto.request.SaveTopicItemPositionRequest;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemPositionResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemProgressResponse;
import fit.iuh.engolearn.topic.dto.response.TopicProgressResponse;
import fit.iuh.engolearn.topic.exception.LearningPreconditionFailedException;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.UserTopicAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import fit.iuh.engolearn.topic.model.UserTopicItemProgress;
import fit.iuh.engolearn.topic.model.UserTopicProgress;
import fit.iuh.engolearn.topic.model.enums.AttemptScope;
import fit.iuh.engolearn.topic.model.enums.AttemptStatus;
import fit.iuh.engolearn.topic.model.enums.AttemptType;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.ItemProgressStatus;
import fit.iuh.engolearn.topic.model.enums.LearningStage;
import fit.iuh.engolearn.topic.model.vo.DialogueTurnVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionExampleVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.model.vo.FurthestReachedVo;
import fit.iuh.engolearn.topic.model.vo.ItemWordVo;
import fit.iuh.engolearn.topic.model.vo.QuickCheckVo;
import fit.iuh.engolearn.topic.model.vo.QuizSentenceVo;
import fit.iuh.engolearn.topic.model.vo.TopicContextVo;
import fit.iuh.engolearn.topic.model.vo.WordSampleVo;
import fit.iuh.engolearn.topic.model.vo.WordSnapshotVo;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.repository.UserTopicAttemptRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemAttemptRepository;
import fit.iuh.engolearn.topic.repository.UserTopicItemProgressRepository;
import fit.iuh.engolearn.topic.repository.UserTopicProgressRepository;
import fit.iuh.engolearn.topic.utils.CurrentUserProvider;
import fit.iuh.engolearn.topic.utils.LearnerLevelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the learning service with mocked repositories.
 *
 * <p>What these tests prove: which documents the service reads and writes for every
 * business case, that the resume mark only moves forward, that the preconditions reject
 * the cases the business rules forbid, and that a failing write aborts the whole call.
 * What they do not prove: that MongoDB rolls the writes back when the transaction is
 * aborted. That needs a replica set, which the test environment does not provide.
 */
@ExtendWith(MockitoExtension.class)
class TopicLearningServiceImplTest {
    @org.mockito.Mock
    private TopicGenerationService generations;


    private static final String USER = "user-mock-001";
    private static final String TOPIC_ID = "t1";
    private static final String RELEASE_ID = "v1";
    private static final String ITEM_ONE = "i1";
    private static final String ITEM_TWO = "i2";
    private static final String VERSION_ONE = "iv1";
    private static final String VERSION_TWO = "iv2";

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private TopicVersionRepository topicVersionRepository;

    @Mock
    private TopicItemRepository topicItemRepository;

    @Mock
    private TopicItemVersionRepository topicItemVersionRepository;

    @Mock
    private UserTopicProgressRepository userTopicProgressRepository;

    @Mock
    private UserTopicItemProgressRepository userTopicItemProgressRepository;

    @Mock
    private UserTopicAttemptRepository userTopicAttemptRepository;

    @Mock
    private UserTopicItemAttemptRepository userTopicItemAttemptRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private LearnerLevelProvider learnerLevelProvider;

    private TopicLearningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TopicLearningServiceImpl(topicRepository, topicVersionRepository, topicItemRepository,
                topicItemVersionRepository, userTopicProgressRepository, userTopicItemProgressRepository,
                userTopicAttemptRepository, userTopicItemAttemptRepository, currentUserProvider,
                learnerLevelProvider, generations);
    }

    // --- content ------------------------------------------------------------------------

    @Test
    void contentReturnsTheContextVocabulariesAndExpressionsOfThePublishedVersion() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());

        TopicItemContentResponse content = service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build());

        assertThat(content.getTopicItemId()).isEqualTo(ITEM_ONE);
        assertThat(content.getTopicItemVersionId()).isEqualTo(VERSION_ONE);
        assertThat(content.getVersion()).isEqualTo(1);
        assertThat(content.getTitle()).isEqualTo("Item one");
        assertThat(content.getContext().getScenarioDescription()).isEqualTo("At the airport");
        assertThat(content.getContext().getPromptingQuestion()).isEqualTo("Where do you go?");
        assertThat(content.getContext().getDialogue()).hasSize(1);
        assertThat(content.getContext().getDialogue().get(0).getTextEn()).isEqualTo("Hello");
        assertThat(content.getVocabularies()).hasSize(1);
        assertThat(content.getVocabularies().get(0).getWord()).isEqualTo("airport");
        assertThat(content.getVocabularies().get(0).getPronunciation()).isEqualTo("/airport/");
        assertThat(content.getVocabularies().get(0).getMeaningVi()).isEqualTo("san bay");
        assertThat(content.getVocabularies().get(0).getAudioUrl()).isEqualTo("airport.mp3");
        assertThat(content.getVocabularies().get(0).getExamples()).hasSize(1);
        assertThat(content.getExpressions()).hasSize(1);
        assertThat(content.getExpressions().get(0).getPurposes()).hasSize(1);
        assertThat(content.getExpressions().get(0).getPurposes().get(0).getExamples()).hasSize(1);
    }

    @Test
    void quickCheckIsReadFromTheStoredMapsAndFallsBackToTheSentence() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());

        TopicItemContentResponse content = service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build());

        var quickCheck = content.getVocabularies().get(0).getQuickCheck();
        assertThat(quickCheck.getQuestion()).isEqualTo("Fill in the blank");
        assertThat(quickCheck.getOptions()).containsExactly("a", "b");
        assertThat(quickCheck.getCorrectAnswer()).isEqualTo("airport");
    }

    @Test
    void quickCheckFallsBackToTheSentenceWhenNoQuestionIsStored() {
        stubSignedIn();
        stubTopicItems();
        TopicItemVersion version = versionOne();
        version.getVocabularies().get(0).setQuickCheck(QuickCheckVo.builder()
                .sentence(QuizSentenceVo.builder().textEn("Fill in the blank: the ___").build())
                .quizData(Map.of("options", List.of("a", "b")))
                .answerData(Map.of("answer", "airport"))
                .build());
        when(topicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(topic()));
        when(topicVersionRepository.findById(RELEASE_ID)).thenReturn(Optional.of(release()));
        when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of(version));
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());

        TopicItemContentResponse content = service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build());

        assertThat(content.getVocabularies().get(0).getQuickCheck().getQuestion())
                .isEqualTo("Fill in the blank: the ___");
        assertThat(content.getVocabularies().get(0).getQuickCheck().getCorrectAnswer()).isEqualTo("airport");
    }

    @Test
    void contentFollowsTheVersionFrozenByTheRunningAttempt() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        TopicItemVersion frozen = TopicItemVersion.builder()
                .id("iv-old").topicItemId(ITEM_ONE).topicId(TOPIC_ID).version(1)
                .status(ContentStatus.PUBLISHED).title("Old version").build();
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE))
                .thenReturn(List.of(itemAttempt("ia1", "iv-old", LearningStage.VOCAB, 2)));
        when(topicItemVersionRepository.findById("iv-old")).thenReturn(Optional.of(frozen));

        TopicItemContentResponse content = service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build());

        assertThat(content.getTopicItemVersionId()).isEqualTo("iv-old");
        assertThat(content.getTitle()).isEqualTo("Old version");
    }

    @Test
    void contentOfAnItemThatDoesNotExistIsNotFound() {
        stubSignedIn();
        when(topicItemRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId("nope").build()))
                .isInstanceOf(TopicItemNotFoundException.class);
    }

    @Test
    void contentOfAnItemThatIsNotPublishedIsNotFound() {
        stubSignedIn();
        when(topicItemRepository.findById(ITEM_ONE)).thenReturn(Optional.of(topicItem(ITEM_ONE)));
        when(topicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(topic()));
        when(topicVersionRepository.findById(RELEASE_ID)).thenReturn(Optional.of(release()));
        when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of(versionTwo()));

        assertThatThrownBy(() -> service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build()))
                .isInstanceOf(TopicItemNotFoundException.class);
    }

    @Test
    void contentOfAnItemOfAnUnpublishedTopicIsNotFound() {
        stubSignedIn();
        when(topicItemRepository.findById(ITEM_ONE)).thenReturn(Optional.of(topicItem(ITEM_ONE)));
        when(topicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(Topic.builder()
                .id(TOPIC_ID).cefrLevel(CefrLevel.A2).build()));

        assertThatThrownBy(() -> service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build()))
                .isInstanceOf(TopicItemNotFoundException.class);
    }

    @Test
    void aGuestCannotReadTheContent() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("  ");

        assertThatThrownBy(() -> service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build()))
                .isInstanceOf(LearningPreconditionFailedException.class);
        verifyNoInteractions(topicItemRepository);
    }

    @Test
    void contentOfAnItemWithoutTheOptionalPartsIsStillReturned() {
        stubSignedIn();
        when(topicItemRepository.findById(ITEM_ONE)).thenReturn(Optional.of(topicItem(ITEM_ONE)));
        when(topicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(topic()));
        when(topicVersionRepository.findById(RELEASE_ID)).thenReturn(Optional.of(release()));
        when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of(TopicItemVersion.builder()
                .id(VERSION_ONE).topicItemId(ITEM_ONE).topicId(TOPIC_ID).version(1).order(1)
                .title("Bare item").status(ContentStatus.PUBLISHED).build()));
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());

        TopicItemContentResponse content = service.getItemContent(
                GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build());

        assertThat(content.getTitle()).isEqualTo("Bare item");
        assertThat(content.getContext()).isNull();
        assertThat(content.getVocabularies()).isNull();
        assertThat(content.getExpressions()).isNull();
    }

    @Test
    void readingTheContentAndThePositionNeverCreatesProgress() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());
        when(userTopicItemProgressRepository.findByUserId(USER))
                .thenReturn(List.of());

        service.getItemContent(GetTopicItemContentRequest.builder().topicItemId(ITEM_ONE).build());
        service.getItemPosition(GetTopicItemPositionRequest.builder().topicItemId(ITEM_ONE).build());

        // Opening an item and leaving without pressing Continue must not start it.
        verify(userTopicAttemptRepository, never()).save(any());
        verify(userTopicItemAttemptRepository, never()).save(any());
        verify(userTopicItemProgressRepository, never()).save(any());
        verify(userTopicProgressRepository, never()).save(any());
    }

    // --- position ----------------------------------------------------------------------

    @Test
    void positionIsNotStartedWhenNothingWasSavedYet() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());
        when(userTopicItemProgressRepository.findByUserId(USER))
                .thenReturn(List.of());

        TopicItemPositionResponse position = service.getItemPosition(
                GetTopicItemPositionRequest.builder().topicItemId(ITEM_ONE).build());

        assertThat(position.getStatus()).isEqualTo(ItemProgressStatus.NOT_STARTED);
        assertThat(position.getStage()).isNull();
        assertThat(position.getItemIndex()).isNull();
        assertThat(position.getTopicItemVersionId()).isEqualTo(VERSION_ONE);
    }

    @Test
    void positionReturnsTheStoredStageAndIndex() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE))
                .thenReturn(List.of(itemAttempt("ia1", VERSION_ONE, LearningStage.EXPRESSIONS, 1)));
        when(userTopicItemProgressRepository.findByUserId(USER))
                .thenReturn(List.of(itemProgress(ITEM_ONE, ItemProgressStatus.IN_PROGRESS)));

        TopicItemPositionResponse position = service.getItemPosition(
                GetTopicItemPositionRequest.builder().topicItemId(ITEM_ONE).build());

        assertThat(position.getStatus()).isEqualTo(ItemProgressStatus.IN_PROGRESS);
        assertThat(position.getStage()).isEqualTo(LearningStage.EXPRESSIONS);
        assertThat(position.getItemIndex()).isEqualTo(1);
    }

    @Test
    void positionIsInProgressFromTheRunningAttemptWhenTheProgressDocumentIsMissing() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE))
                .thenReturn(List.of(itemAttempt("ia1", VERSION_ONE, LearningStage.VOCAB, 0)));
        when(userTopicItemProgressRepository.findByUserId(USER))
                .thenReturn(List.of());

        TopicItemPositionResponse position = service.getItemPosition(
                GetTopicItemPositionRequest.builder().topicItemId(ITEM_ONE).build());

        assertThat(position.getStatus()).isEqualTo(ItemProgressStatus.IN_PROGRESS);
        assertThat(position.getStage()).isEqualTo(LearningStage.VOCAB);
    }

    // --- saving the position -----------------------------------------------------------

    @Test
    void savingThePositionWritesTheAttemptTheItemAttemptAndBothProgressDocuments() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        stubNothingStartedYet();
        stubSaves();

        TopicItemPositionResponse position = service.saveItemPosition(
                SaveTopicItemPositionRequest.builder()
                        .topicItemId(ITEM_ONE).stage(LearningStage.VOCAB).itemIndex(2).build());

        ArgumentCaptor<UserTopicAttempt> attempt = ArgumentCaptor.forClass(UserTopicAttempt.class);
        verify(userTopicAttemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getAttemptType()).isEqualTo(AttemptType.LEARNING);
        assertThat(attempt.getValue().getScope()).isEqualTo(AttemptScope.ITEM);
        assertThat(attempt.getValue().getTopicVersionId()).isEqualTo(RELEASE_ID);
        assertThat(attempt.getValue().getTargetTopicItemId()).isEqualTo(ITEM_ONE);
        assertThat(attempt.getValue().getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);

        ArgumentCaptor<UserTopicItemAttempt> itemAttempt = ArgumentCaptor.forClass(UserTopicItemAttempt.class);
        verify(userTopicItemAttemptRepository).save(itemAttempt.capture());
        assertThat(itemAttempt.getValue().getTopicItemVersionId()).isEqualTo(VERSION_ONE);
        assertThat(itemAttempt.getValue().getFurthestReached().getStage()).isEqualTo(LearningStage.VOCAB);
        assertThat(itemAttempt.getValue().getFurthestReached().getItemIndex()).isEqualTo(2);

        ArgumentCaptor<UserTopicItemProgress> itemProgress = ArgumentCaptor.forClass(UserTopicItemProgress.class);
        verify(userTopicItemProgressRepository).save(itemProgress.capture());
        assertThat(itemProgress.getValue().getStatus()).isEqualTo(ItemProgressStatus.IN_PROGRESS);
        assertThat(itemProgress.getValue().getTopicId()).isEqualTo(TOPIC_ID);

        ArgumentCaptor<UserTopicProgress> topicProgress = ArgumentCaptor.forClass(UserTopicProgress.class);
        verify(userTopicProgressRepository).save(topicProgress.capture());
        assertThat(topicProgress.getValue().getLastTopicItemId()).isEqualTo(ITEM_ONE);

        assertThat(position.getStatus()).isEqualTo(ItemProgressStatus.IN_PROGRESS);
        assertThat(position.getStage()).isEqualTo(LearningStage.VOCAB);
        assertThat(position.getItemIndex()).isEqualTo(2);
    }

    @Test
    void savingThePositionNeverMovesTheResumeMarkBackwards() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        stubNothingStartedYet();
        stubSaves();
        when(userTopicItemAttemptRepository.findByUserTopicAttemptIdAndTopicItemId(anyString(), anyString()))
                .thenReturn(Optional.of(itemAttempt("ia1", VERSION_ONE, LearningStage.EXPRESSIONS, 3)));

        service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.CONTEXT).itemIndex(0).build());

        ArgumentCaptor<UserTopicItemAttempt> saved = ArgumentCaptor.forClass(UserTopicItemAttempt.class);
        verify(userTopicItemAttemptRepository).save(saved.capture());
        assertThat(saved.getValue().getFurthestReached().getStage()).isEqualTo(LearningStage.EXPRESSIONS);
        assertThat(saved.getValue().getFurthestReached().getItemIndex()).isEqualTo(3);
    }

    @Test
    void savingThePositionMovesTheResumeMarkForward() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        stubNothingStartedYet();
        stubSaves();
        when(userTopicItemAttemptRepository.findByUserTopicAttemptIdAndTopicItemId(anyString(), anyString()))
                .thenReturn(Optional.of(itemAttempt("ia1", VERSION_ONE, LearningStage.VOCAB, 1)));

        service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.EXPRESSIONS).itemIndex(0).build());

        ArgumentCaptor<UserTopicItemAttempt> saved = ArgumentCaptor.forClass(UserTopicItemAttempt.class);
        verify(userTopicItemAttemptRepository).save(saved.capture());
        assertThat(saved.getValue().getFurthestReached().getStage()).isEqualTo(LearningStage.EXPRESSIONS);
        assertThat(saved.getValue().getFurthestReached().getItemIndex()).isEqualTo(0);
    }

    @Test
    void savingThePositionKeepsTheVersionFrozenByTheRunningItemAttempt() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        when(userTopicItemProgressRepository.findByUserId(USER)).thenReturn(List.of());
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of());
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE))
                .thenReturn(List.of(itemAttempt("ia1", "iv-old", LearningStage.VOCAB, 0)));
        when(topicItemVersionRepository.findById("iv-old"))
                .thenReturn(Optional.of(TopicItemVersion.builder()
                        .id("iv-old").topicItemId(ITEM_ONE).topicId(TOPIC_ID).version(1)
                        .status(ContentStatus.PUBLISHED).build()));
        when(userTopicItemAttemptRepository.findByUserTopicAttemptIdAndTopicItemId(anyString(), anyString()))
                .thenReturn(Optional.of(itemAttempt("ia1", "iv-old", LearningStage.VOCAB, 0)));
        stubSaves();

        service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.VOCAB).itemIndex(1).build());

        ArgumentCaptor<UserTopicItemAttempt> saved = ArgumentCaptor.forClass(UserTopicItemAttempt.class);
        verify(userTopicItemAttemptRepository).save(saved.capture());
        assertThat(saved.getValue().getTopicItemVersionId()).isEqualTo("iv-old");
    }

    @Test
    void savingThePositionReusesTheRunningAttemptOfTheTopic() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        stubNothingStartedYet();
        stubSaves();
        UserTopicAttempt running = learningAttempt("a1", ITEM_ONE);
        when(userTopicAttemptRepository.findByUserIdAndTopicIdAndAttemptTypeAndStatus(
                USER, TOPIC_ID, AttemptType.LEARNING, AttemptStatus.IN_PROGRESS)).thenReturn(List.of(running));

        service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.CONTEXT).itemIndex(0).build());

        ArgumentCaptor<UserTopicAttempt> saved = ArgumentCaptor.forClass(UserTopicAttempt.class);
        verify(userTopicAttemptRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("a1");
        assertThat(saved.getValue().getStartedAt()).isEqualTo(running.getStartedAt());
    }

    @Test
    void savingThePositionIsRejectedWhenAnotherItemOfTheTopicIsStillBeingLearned() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        when(userTopicItemProgressRepository.findByUserId(USER)).thenReturn(List.of());
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(USER, TOPIC_ID))
                .thenReturn(List.of(itemProgress(ITEM_TWO, ItemProgressStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.CONTEXT).itemIndex(0).build()))
                .isInstanceOf(LearningPreconditionFailedException.class);
        verify(userTopicAttemptRepository, never()).save(any());
    }

    @Test
    void savingThePositionIsRejectedWhenAPreviousItemIsNotCompleted() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        when(userTopicItemProgressRepository.findByUserId(USER)).thenReturn(List.of());
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_TWO)).thenReturn(List.of());
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(USER, TOPIC_ID))
                .thenReturn(List.of(itemProgress(ITEM_ONE, ItemProgressStatus.NOT_STARTED)));

        assertThatThrownBy(() -> service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_TWO).stage(LearningStage.CONTEXT).itemIndex(0).build()))
                .isInstanceOf(LearningPreconditionFailedException.class);
        verify(userTopicAttemptRepository, never()).save(any());
    }

    @Test
    void savingThePositionIsRejectedWhenTheTopicIsAboveTheLearnerLevel() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(learnerLevelProvider.getCurrentLevel()).thenReturn(CefrLevel.A1);
        when(userTopicItemProgressRepository.findByUserId(USER)).thenReturn(List.of());
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.CONTEXT).itemIndex(0).build()))
                .isInstanceOf(LearningPreconditionFailedException.class);
        verify(userTopicAttemptRepository, never()).save(any());
    }

    @Test
    void savingThePositionOfACompletedItemIsRejected() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        when(userTopicItemProgressRepository.findByUserId(USER))
                .thenReturn(List.of(itemProgress(ITEM_ONE, ItemProgressStatus.COMPLETED)));
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(USER, ITEM_ONE)).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.CONTEXT).itemIndex(0).build()))
                .isInstanceOf(LearningPreconditionFailedException.class);
        verify(userTopicAttemptRepository, never()).save(any());
    }

    @Test
    void savingThePositionOfAnItemThatDoesNotExistIsNotFound() {
        stubSignedIn();
        when(topicItemRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId("nope").stage(LearningStage.CONTEXT).itemIndex(0).build()))
                .isInstanceOf(TopicItemNotFoundException.class);
    }

    @Test
    void aFailingWriteAbortsTheWholeCallInsteadOfLeavingAPartialPosition() {
        stubPublishedRelease();
        stubTopicItems();
        stubSignedIn();
        stubLearnerLevel();
        stubNothingStartedYet();
        when(userTopicAttemptRepository.save(any(UserTopicAttempt.class))).thenAnswer(invocation -> {
            UserTopicAttempt attempt = invocation.getArgument(0);
            attempt.setId("a1");
            return attempt;
        });
        when(userTopicItemAttemptRepository.findByUserTopicAttemptIdAndTopicItemId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userTopicItemAttemptRepository.save(any(UserTopicItemAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userTopicItemProgressRepository.save(any(UserTopicItemProgress.class)))
                .thenThrow(new IllegalStateException("write failed"));

        assertThatThrownBy(() -> service.saveItemPosition(SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(LearningStage.VOCAB).itemIndex(0).build()))
                .isInstanceOf(IllegalStateException.class);
        // The topic progress is never touched once a write of the operation failed.
        verify(userTopicProgressRepository, never()).save(any());
    }

    @Test
    void theSaveMethodIsDeclaredTransactional() throws NoSuchMethodException {
        assertThat(TopicLearningServiceImpl.class
                .getMethod("saveItemPosition", SaveTopicItemPositionRequest.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    // --- topic progress ----------------------------------------------------------------

    @Test
    void topicProgressCountsTheVisibleItemsAndDerivesTheStatus() {
        stubPublishedRelease();
        stubSignedIn();
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of(
                itemProgress(ITEM_ONE, ItemProgressStatus.COMPLETED),
                itemProgress(ITEM_TWO, ItemProgressStatus.IN_PROGRESS)));
        when(userTopicItemAttemptRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of());
        when(userTopicProgressRepository.findByUserId(USER)).thenReturn(List.of());

        TopicProgressResponse progress = service.getTopicProgress(
                GetTopicProgressRequest.builder().topicId(TOPIC_ID).build());

        assertThat(progress.getTopicId()).isEqualTo(TOPIC_ID);
        assertThat(progress.getStatus()).isEqualTo(ItemProgressStatus.IN_PROGRESS);
        assertThat(progress.getCompletedItemCount()).isEqualTo(1);
        assertThat(progress.getTotalItemCount()).isEqualTo(2);
        assertThat(progress.getItems()).extracting(TopicItemProgressResponse::getStatus).containsExactly(
                ItemProgressStatus.COMPLETED, ItemProgressStatus.IN_PROGRESS);
    }

    @Test
    void topicProgressIsNotStartedWithoutAnyLearning() {
        stubPublishedRelease();
        stubSignedIn();
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of());
        when(userTopicItemAttemptRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of());
        when(userTopicProgressRepository.findByUserId(USER)).thenReturn(List.of());

        TopicProgressResponse progress = service.getTopicProgress(
                GetTopicProgressRequest.builder().topicId(TOPIC_ID).build());

        assertThat(progress.getStatus()).isEqualTo(ItemProgressStatus.NOT_STARTED);
        assertThat(progress.getCompletedItemCount()).isZero();
        assertThat(progress.getTotalItemCount()).isEqualTo(2);
        assertThat(progress.getItems()).extracting(TopicItemProgressResponse::getStatus)
                .containsOnly(ItemProgressStatus.NOT_STARTED);
    }

    @Test
    void topicProgressIsCompletedWhenEveryVisibleItemIsCompleted() {
        stubPublishedRelease();
        stubSignedIn();
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of(
                itemProgress(ITEM_ONE, ItemProgressStatus.COMPLETED),
                itemProgress(ITEM_TWO, ItemProgressStatus.COMPLETED)));
        when(userTopicItemAttemptRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of());
        when(userTopicProgressRepository.findByUserId(USER))
                .thenReturn(List.of(UserTopicProgress.builder()
                        .id("tp1").userId(USER).topicId(TOPIC_ID)
                        .lastAccessedAt(Instant.parse("2026-01-01T00:00:00Z")).build()));

        TopicProgressResponse progress = service.getTopicProgress(
                GetTopicProgressRequest.builder().topicId(TOPIC_ID).build());

        assertThat(progress.getStatus()).isEqualTo(ItemProgressStatus.COMPLETED);
        assertThat(progress.getCompletedItemCount()).isEqualTo(2);
        assertThat(progress.getLastAccessedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void topicProgressCountsARunningAttemptEvenWithoutAProgressDocument() {
        stubPublishedRelease();
        stubSignedIn();
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(USER, TOPIC_ID)).thenReturn(List.of());
        when(userTopicItemAttemptRepository.findByUserIdAndTopicId(USER, TOPIC_ID))
                .thenReturn(List.of(itemAttempt("ia1", VERSION_ONE, LearningStage.VOCAB, 0)));
        when(userTopicProgressRepository.findByUserId(USER)).thenReturn(List.of());

        TopicProgressResponse progress = service.getTopicProgress(
                GetTopicProgressRequest.builder().topicId(TOPIC_ID).build());

        assertThat(progress.getStatus()).isEqualTo(ItemProgressStatus.IN_PROGRESS);
        assertThat(progress.getItems().get(0).getStatus()).isEqualTo(ItemProgressStatus.IN_PROGRESS);
    }

    // --- fixtures ----------------------------------------------------------------------

    private void stubPublishedRelease() {
        when(topicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(topic()));
        when(topicVersionRepository.findById(RELEASE_ID)).thenReturn(Optional.of(release()));
        when(topicItemVersionRepository.findAllById(anyList()))
                .thenReturn(List.of(versionOne(), versionTwo()));
    }

    private void stubTopicItems() {
        when(topicItemRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.of(TopicItem.builder().id(id).topicId(TOPIC_ID).build());
        });
    }

    private void stubSignedIn() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);
    }

    private void stubLearnerLevel() {
        when(learnerLevelProvider.getCurrentLevel()).thenReturn(CefrLevel.C2);
    }

    /** No progress and no running attempt yet, so the item counts as not started. */
    private void stubNothingStartedYet() {
        when(userTopicItemProgressRepository.findByUserId(anyString()))
                .thenReturn(List.of());
        when(userTopicItemProgressRepository.findByUserIdAndTopicId(anyString(), anyString()))
                .thenReturn(List.of());
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(anyString(), anyString()))
                .thenReturn(List.of());
    }

    private void stubSaves() {
        when(userTopicAttemptRepository.save(any(UserTopicAttempt.class))).thenAnswer(invocation -> {
            UserTopicAttempt attempt = invocation.getArgument(0);
            if (attempt.getId() == null) {
                attempt.setId("a1");
            }
            return attempt;
        });
        when(userTopicItemAttemptRepository.save(any(UserTopicItemAttempt.class))).thenAnswer(invocation -> {
            UserTopicItemAttempt itemAttempt = invocation.getArgument(0);
            if (itemAttempt.getId() == null) {
                itemAttempt.setId("ia1");
            }
            return itemAttempt;
        });
        when(userTopicItemProgressRepository.save(any(UserTopicItemProgress.class))).thenAnswer(invocation -> {
            UserTopicItemProgress progress = invocation.getArgument(0);
            if (progress.getId() == null) {
                progress.setId("p1");
            }
            return progress;
        });
        when(userTopicProgressRepository.findByUserId(anyString()))
                .thenReturn(List.of());
        when(userTopicProgressRepository.save(any(UserTopicProgress.class))).thenAnswer(invocation -> {
            UserTopicProgress progress = invocation.getArgument(0);
            if (progress.getId() == null) {
                progress.setId("tp1");
            }
            return progress;
        });
    }

    private Topic topic() {
        return Topic.builder()
                .id(TOPIC_ID)
                .titleEn("Travel")
                .cefrLevel(CefrLevel.A2)
                .latestPublishedVersion(RELEASE_ID)
                .build();
    }

    private TopicItem topicItem(String id) {
        return TopicItem.builder().id(id).topicId(TOPIC_ID).build();
    }

    private TopicVersion release() {
        return TopicVersion.builder()
                .id(RELEASE_ID)
                .topicId(TOPIC_ID)
                .version(1)
                .status(ContentStatus.PUBLISHED)
                .itemVersionIds(List.of(VERSION_ONE, VERSION_TWO))
                .build();
    }

    private TopicItemVersion versionOne() {
        return TopicItemVersion.builder()
                .id(VERSION_ONE)
                .topicItemId(ITEM_ONE)
                .topicId(TOPIC_ID)
                .topicVersionId(RELEASE_ID)
                .version(1)
                .order(1)
                .title("Item one")
                .status(ContentStatus.PUBLISHED)
                .context(TopicContextVo.builder()
                        .scenarioDescription("At the airport")
                        .prompt("Where do you go?")
                        .dialogue(List.of(DialogueTurnVo.builder()
                                .speaker("A").textEn("Hello").textVi("Xin chao").build()))
                        .durationSec(60)
                        .tips("Listen carefully")
                        .build())
                .vocabularies(List.of(ItemWordVo.builder()
                        .vocabId("v1")
                        .senseId("s1")
                        .snapshot(WordSnapshotVo.builder()
                                .word("airport")
                                .pos("noun")
                                .describeVi("san bay")
                                .phonetic("/airport/")
                                .audioUrl("airport.mp3")
                                .build())
                        .samples(List.of(WordSampleVo.builder()
                                .textEn("I am at the airport")
                                .textVi("Toi o san bay")
                                .audioUrl("sample.mp3")
                                .build()))
                        .quickCheck(QuickCheckVo.builder()
                                .sentence(QuizSentenceVo.builder()
                                        .textEn("Fill in the blank: the ___")
                                        .build())
                                .quizData(Map.of("question", "Fill in the blank", "options", List.of("a", "b")))
                                .answerData(Map.of("correct_answer", "airport"))
                                .build())
                        .orderIndex(1)
                        .build()))
                .expressions(List.of(ExpressionBlockVo.builder()
                        .purposes(List.of(ExpressionPurposeVo.builder()
                                .purposeEn("Ask for help")
                                .purposeVi("Hoi xin tro giup")
                                .orderIndex(1)
                                .examples(List.of(ExpressionExampleVo.builder()
                                        .textEn("Could you help me?")
                                        .textVi("Ban giup toi duoc khong?")
                                        .usageNote("Polite request")
                                        .build()))
                                .build()))
                        .build()))
                .build();
    }

    private TopicItemVersion versionTwo() {
        return TopicItemVersion.builder()
                .id(VERSION_TWO)
                .topicItemId(ITEM_TWO)
                .topicId(TOPIC_ID)
                .topicVersionId(RELEASE_ID)
                .version(1)
                .order(2)
                .title("Item two")
                .status(ContentStatus.PUBLISHED)
                .build();
    }

    private UserTopicItemProgress itemProgress(String topicItemId, ItemProgressStatus status) {
        return UserTopicItemProgress.builder()
                .id("p-" + topicItemId)
                .userId(USER)
                .topicId(TOPIC_ID)
                .topicItemId(topicItemId)
                .status(status)
                .build();
    }

    private UserTopicAttempt learningAttempt(String id, String topicItemId) {
        return UserTopicAttempt.builder()
                .id(id)
                .userId(USER)
                .topicId(TOPIC_ID)
                .attemptType(AttemptType.LEARNING)
                .scope(AttemptScope.ITEM)
                .topicVersionId(RELEASE_ID)
                .targetTopicItemId(topicItemId)
                .currentTopicItemId(topicItemId)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .lastAccessedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private UserTopicItemAttempt itemAttempt(String id, String versionId, LearningStage stage, int itemIndex) {
        return UserTopicItemAttempt.builder()
                .id(id)
                .userTopicAttemptId("a1")
                .userId(USER)
                .topicId(TOPIC_ID)
                .topicItemId(ITEM_ONE)
                .topicItemVersionId(versionId)
                .status(AttemptStatus.IN_PROGRESS)
                .furthestReached(FurthestReachedVo.builder().stage(stage).itemIndex(itemIndex).build())
                .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .lastAccessedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }
}
