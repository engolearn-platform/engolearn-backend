package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.request.SaveTopicItemPositionRequest;
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
import fit.iuh.engolearn.topic.model.vo.FurthestReachedVo;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Concurrent writes to the same position of the same learner.
 *
 * <p>The store is simulated in memory and behaves like MongoDB on the two points that
 * matter here: a read returns a detached copy, and a write of a document that changed in
 * the meantime is rejected instead of overwriting the newer state.
 */
@ExtendWith(MockitoExtension.class)
class TopicLearningServiceConcurrencyTest {

    private static final String USER = "user-mock-001";
    private static final String TOPIC_ID = "t1";
    private static final String RELEASE_ID = "v1";
    private static final String ITEM_ONE = "i1";
    private static final String VERSION_ONE = "iv1";

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

    /** The store the stubs read from; it is replaced between the rounds of the race. */
    private FakeStore store;

    @BeforeEach
    void setUp() {
        service = new TopicLearningServiceImpl(topicRepository, topicVersionRepository, topicItemRepository,
                topicItemVersionRepository, userTopicProgressRepository, userTopicItemProgressRepository,
                userTopicAttemptRepository, userTopicItemAttemptRepository, currentUserProvider,
                learnerLevelProvider);
    }

    @Test
    void concurrentSavesOfTheSameItemNeverMoveTheResumeMarkBackwards() throws Exception {
        stubContent();
        stubStore();
        List<SaveTopicItemPositionRequest> requests = List.of(
                request(LearningStage.VOCAB, 2),
                request(LearningStage.VOCAB, 3),
                request(LearningStage.EXPRESSIONS, 0),
                request(LearningStage.CONTEXT, 0),
                request(LearningStage.VOCAB, 1),
                request(LearningStage.EXPRESSIONS, 1),
                request(LearningStage.FILL_BLANK, 0),
                request(LearningStage.SITUATIONAL_CHOICE, 0));

        for (int round = 0; round < 25; round++) {
            store = new FakeStore(requests.size());
            store.seedItemAttempt(LearningStage.VOCAB, 2);

            List<Throwable> failures = runConcurrently(requests);

            // Every request read the same state, so exactly one of them may write it.
            assertThat(failures).hasSize(requests.size() - 1);
            assertThat(failures).allSatisfy(failure ->
                    assertThat(failure).isInstanceOf(OptimisticLockingFailureException.class));

            UserTopicItemAttempt stored = store.itemAttempts.get(store.itemAttemptKey());
            assertThat(stored).isNotNull();
            assertThat(store.itemAttempts).hasSize(1);
            assertThat(store.attempts).hasSize(1);
            // The mark that was stored before the race is never given up.
            assertThat(isAtLeast(stored.getFurthestReached(), LearningStage.VOCAB, 2)).isTrue();
        }
    }

    private List<Throwable> runConcurrently(List<SaveTopicItemPositionRequest> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Throwable>> results = new ArrayList<>();
            for (SaveTopicItemPositionRequest request : requests) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.saveItemPosition(request);
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> result : results) {
                Throwable failure = result.get(30, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private void stubContent() {
        when(topicItemRepository.findById(anyString()))
                .thenReturn(Optional.of(TopicItem.builder().id(ITEM_ONE).topicId(TOPIC_ID).build()));
        when(topicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(Topic.builder()
                .id(TOPIC_ID).cefrLevel(CefrLevel.A2).latestPublishedVersion(RELEASE_ID).build()));
        when(topicVersionRepository.findById(RELEASE_ID)).thenReturn(Optional.of(TopicVersion.builder()
                .id(RELEASE_ID).topicId(TOPIC_ID).status(ContentStatus.PUBLISHED)
                .itemVersionIds(List.of(VERSION_ONE)).build()));
        when(topicItemVersionRepository.findAllById(anyList())).thenReturn(List.of(TopicItemVersion.builder()
                .id(VERSION_ONE).topicItemId(ITEM_ONE).topicId(TOPIC_ID).version(1).order(1)
                .status(ContentStatus.PUBLISHED).build()));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);
    }

    private void stubStore() {
        when(userTopicAttemptRepository
                .findByUserIdAndTopicIdAndAttemptTypeAndStatus(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    UserTopicAttempt stored = store.attempts.get(store.attemptKey());
                    return stored == null ? List.of() : List.of(copyOf(stored));
                });
        when(userTopicAttemptRepository.save(any(UserTopicAttempt.class))).thenAnswer(invocation -> {
            UserTopicAttempt candidate = invocation.getArgument(0);
            UserTopicAttempt stored = store.attempts.get(store.attemptKey());
            if (stored == null) {
                candidate.setId("attempt-1");
                store.attempts.put(store.attemptKey(), copyOf(candidate));
                return copyOf(candidate);
            }
            // Only one learning attempt runs per topic, the first one wins.
            return copyOf(stored);
        });
        when(userTopicItemAttemptRepository.findByUserTopicAttemptIdAndTopicItemId(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    UserTopicItemAttempt stored = store.itemAttempts.get(store.itemAttemptKey());
                    // All requests read the same state, so the race is decided by the writes.
                    store.awaitReaders();
                    return stored == null ? Optional.empty() : Optional.of(copyOf(stored));
                });
        when(userTopicItemAttemptRepository.findByUserIdAndTopicItemId(anyString(), anyString()))
                .thenAnswer(invocation -> store.itemAttempts.values().stream()
                        .map(TopicLearningServiceConcurrencyTest::copyOf)
                        .toList());
        when(userTopicItemAttemptRepository.save(any(UserTopicItemAttempt.class))).thenAnswer(invocation -> {
            UserTopicItemAttempt candidate = invocation.getArgument(0);
            return store.itemAttempts.compute(store.itemAttemptKey(), (key, stored) -> {
                if (stored == null) {
                    candidate.setVersion(store.nextVersion());
                    return copyOf(candidate);
                }
                if (!Objects.equals(stored.getVersion(), candidate.getVersion())) {
                    throw new OptimisticLockingFailureException("the position was changed by another request");
                }
                candidate.setVersion(store.nextVersion());
                return copyOf(candidate);
            });
        });
        when(userTopicItemProgressRepository.findByUserIdAndTopicItemId(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        store.itemProgress.get(store.itemProgressKey())).map(TopicLearningServiceConcurrencyTest::copyOf));
        when(userTopicItemProgressRepository.save(any(UserTopicItemProgress.class))).thenAnswer(invocation -> {
            UserTopicItemProgress candidate = invocation.getArgument(0);
            candidate.setId("progress-1");
            store.itemProgress.put(store.itemProgressKey(), copyOf(candidate));
            return copyOf(candidate);
        });
        when(userTopicProgressRepository.findByUserIdAndTopicId(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        store.topicProgress.get(store.topicProgressKey())).map(TopicLearningServiceConcurrencyTest::copyOf));
        when(userTopicProgressRepository.save(any(UserTopicProgress.class))).thenAnswer(invocation -> {
            UserTopicProgress candidate = invocation.getArgument(0);
            candidate.setId("topic-progress-1");
            store.topicProgress.put(store.topicProgressKey(), copyOf(candidate));
            return copyOf(candidate);
        });
    }

    private static SaveTopicItemPositionRequest request(LearningStage stage, int itemIndex) {
        return SaveTopicItemPositionRequest.builder()
                .topicItemId(ITEM_ONE).stage(stage).itemIndex(itemIndex).build();
    }

    /** The business order of the stages decides which mark is the further one. */
    private static boolean isAtLeast(FurthestReachedVo mark, LearningStage stage, int itemIndex) {
        if (mark == null || mark.getStage() == null) {
            return false;
        }
        if (mark.getStage().ordinal() != stage.ordinal()) {
            return mark.getStage().ordinal() > stage.ordinal();
        }
        return mark.getItemIndex() != null && mark.getItemIndex() >= itemIndex;
    }

    private static UserTopicAttempt copyOf(UserTopicAttempt source) {
        return UserTopicAttempt.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .topicId(source.getTopicId())
                .attemptType(source.getAttemptType())
                .scope(source.getScope())
                .topicVersionId(source.getTopicVersionId())
                .targetTopicItemId(source.getTargetTopicItemId())
                .currentTopicItemId(source.getCurrentTopicItemId())
                .status(source.getStatus())
                .startedAt(source.getStartedAt())
                .lastAccessedAt(source.getLastAccessedAt())
                .build();
    }

    private static UserTopicItemAttempt copyOf(UserTopicItemAttempt source) {
        return UserTopicItemAttempt.builder()
                .id(source.getId())
                .version(source.getVersion())
                .userTopicAttemptId(source.getUserTopicAttemptId())
                .userId(source.getUserId())
                .topicId(source.getTopicId())
                .topicItemId(source.getTopicItemId())
                .topicItemVersionId(source.getTopicItemVersionId())
                .status(source.getStatus())
                .furthestReached(source.getFurthestReached())
                .startedAt(source.getStartedAt())
                .lastAccessedAt(source.getLastAccessedAt())
                .build();
    }

    private static UserTopicItemProgress copyOf(UserTopicItemProgress source) {
        return UserTopicItemProgress.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .topicId(source.getTopicId())
                .topicItemId(source.getTopicItemId())
                .status(source.getStatus())
                .lastAccessedAt(source.getLastAccessedAt())
                .build();
    }

    private static UserTopicProgress copyOf(UserTopicProgress source) {
        return UserTopicProgress.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .topicId(source.getTopicId())
                .lastTopicItemId(source.getLastTopicItemId())
                .lastAccessedAt(source.getLastAccessedAt())
                .build();
    }

    /** In-memory stand-ins for the four progress collections. */
    private static final class FakeStore {

        private final Map<String, UserTopicAttempt> attempts = new ConcurrentHashMap<>();
        private final Map<String, UserTopicItemAttempt> itemAttempts = new ConcurrentHashMap<>();
        private final Map<String, UserTopicItemProgress> itemProgress = new ConcurrentHashMap<>();
        private final Map<String, UserTopicProgress> topicProgress = new ConcurrentHashMap<>();
        private final AtomicLong versions = new AtomicLong();
        private final CountDownLatch readers;

        private FakeStore(int readers) {
            this.readers = new CountDownLatch(readers);
        }

        /** Holds every request until all of them have read the state they write back. */
        private void awaitReaders() {
            readers.countDown();
            try {
                if (!readers.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the requests did not reach the read of the position");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }

        private String attemptKey() {
            return USER + "|" + TOPIC_ID;
        }

        private String itemAttemptKey() {
            return "attempt-1|" + ITEM_ONE;
        }

        private String itemProgressKey() {
            return USER + "|" + ITEM_ONE;
        }

        private String topicProgressKey() {
            return USER + "|" + TOPIC_ID;
        }

        private long nextVersion() {
            return versions.incrementAndGet();
        }

        private void seedItemAttempt(LearningStage stage, int itemIndex) {
            UserTopicItemAttempt seeded = UserTopicItemAttempt.builder()
                    .id("item-attempt-1")
                    .version(nextVersion())
                    .userTopicAttemptId("attempt-1")
                    .userId(USER)
                    .topicId(TOPIC_ID)
                    .topicItemId(ITEM_ONE)
                    .topicItemVersionId(VERSION_ONE)
                    .status(AttemptStatus.IN_PROGRESS)
                    .furthestReached(FurthestReachedVo.builder().stage(stage).itemIndex(itemIndex).build())
                    .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .lastAccessedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
            itemAttempts.put(itemAttemptKey(), seeded);
            attempts.put(attemptKey(), UserTopicAttempt.builder()
                    .id("attempt-1")
                    .userId(USER)
                    .topicId(TOPIC_ID)
                    .attemptType(AttemptType.LEARNING)
                    .scope(AttemptScope.ITEM)
                    .topicVersionId(RELEASE_ID)
                    .targetTopicItemId(ITEM_ONE)
                    .currentTopicItemId(ITEM_ONE)
                    .status(AttemptStatus.IN_PROGRESS)
                    .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .lastAccessedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build());
            itemProgress.put(itemProgressKey(), UserTopicItemProgress.builder()
                    .id("progress-1")
                    .userId(USER)
                    .topicId(TOPIC_ID)
                    .topicItemId(ITEM_ONE)
                    .status(ItemProgressStatus.IN_PROGRESS)
                    .build());
        }
    }
}
