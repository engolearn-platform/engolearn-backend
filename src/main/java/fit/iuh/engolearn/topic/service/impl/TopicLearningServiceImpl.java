package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.service.*;
import lombok.RequiredArgsConstructor;

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
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.mapper.TopicItemMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TopicLearningServiceImpl implements TopicLearningService {

    private static final Comparator<TopicItemVersion> ITEM_ORDER =
            Comparator.comparing(TopicItemVersion::getOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TopicItemVersion::getTopicItemId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TopicItemVersion::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<UserTopicItemAttempt> LAST_ACCESSED =
            Comparator.comparing(UserTopicItemAttempt::getLastAccessedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()));

    private final TopicRepository topicRepository;
    private final TopicVersionRepository topicVersionRepository;
    private final TopicItemRepository topicItemRepository;
    private final TopicItemVersionRepository topicItemVersionRepository;
    private final UserTopicProgressRepository userTopicProgressRepository;
    private final UserTopicItemProgressRepository userTopicItemProgressRepository;
    private final UserTopicAttemptRepository userTopicAttemptRepository;
    private final UserTopicItemAttemptRepository userTopicItemAttemptRepository;
    private final CurrentUserProvider currentUserProvider;
    private final LearnerLevelProvider learnerLevelProvider;
    private final TopicGenerationService generations;

    // --- content -----------------------------------------------------------------------

    @Override
    public TopicItemContentResponse getItemContent(GetTopicItemContentRequest request) {
        String userId = requireUserId();
        String topicItemId = request.getTopicItemId();
        ItemContext item = resolvePublishedItem(topicItemId);
        UserTopicItemAttempt itemAttempt = findActiveItemAttempt(userId, topicItemId, item.generation());
        TopicItemVersion version = resolveLearningVersion(item, itemAttempt);
        return TopicItemMapper.toContentResponse(topicItemId, version);
    }

    // --- position ----------------------------------------------------------------------

    @Override
    public TopicItemPositionResponse getItemPosition(GetTopicItemPositionRequest request) {
        String userId = requireUserId();
        String topicItemId = request.getTopicItemId();
        ItemContext item = resolvePublishedItem(topicItemId);
        UserTopicItemAttempt itemAttempt = findActiveItemAttempt(userId, topicItemId, item.generation());
        UserTopicItemProgress progress = findItemProgress(userId, topicItemId, item.generation());
        TopicItemVersion version = resolveLearningVersion(item, itemAttempt);
        FurthestReachedVo furthest = itemAttempt == null ? null : itemAttempt.getFurthestReached();
        return TopicItemPositionResponse.builder()
                .topicItemId(topicItemId)
                .topicItemVersionId(version.getId())
                .status(resolveStatus(progress, itemAttempt))
                .stage(furthest == null ? null : furthest.getStage())
                .itemIndex(furthest == null ? null : furthest.getItemIndex())
                .lastAccessedAt(itemAttempt == null ? null : itemAttempt.getLastAccessedAt())
                .build();
    }

    /**
     * Stores the position and marks the item as being learned. The attempt, the item attempt
     * and the item progress are one logical operation, so they are written in a single
     * transaction: a failure anywhere leaves none of them applied.
     */
    @Override
    @Transactional
    public TopicItemPositionResponse saveItemPosition(SaveTopicItemPositionRequest request) {
        if (request == null) {
            throw new LearningPreconditionFailedException("Request is required");
        }
        String userId = requireUserId();
        String topicItemId = request.getTopicItemId();
        LearningStage stage = request.getStage();
        int itemIndex = request.getItemIndex();

        ItemContext item = resolvePublishedItem(topicItemId);
        UserTopicItemProgress progress = findItemProgress(userId, topicItemId, item.generation());
        UserTopicItemAttempt itemAttempt = findActiveItemAttempt(userId, topicItemId, item.generation());

        if (progress != null && progress.getStatus() == ItemProgressStatus.COMPLETED) {
            throw new LearningPreconditionFailedException(
                    "Topic item " + topicItemId + " is already completed, its position is read only");
        }
        if (progress == null || progress.getStatus() != ItemProgressStatus.IN_PROGRESS) {
            // The level and the sequence are checked when an item is started, never when the
            // learner continues one they already started.
            requireLevelAllowed(item.topic());
            requireNoOtherItemInProgress(userId, item, topicItemId);
            requirePredecessorsCompleted(userId, item, topicItemId);
        }

        Instant now = Instant.now();
        UserTopicAttempt attempt = resolveLearningAttempt(userId, item, topicItemId, now);
        TopicItemVersion version = resolveVersionForItemAttempt(item, topicItemId, attempt, itemAttempt);
        UserTopicItemAttempt savedItemAttempt = upsertItemAttempt(userId, attempt, topicItemId, version,
                stage, itemIndex, now);
        upsertItemProgress(userId, item.topic().getId(), topicItemId, now, item.generation());
        upsertTopicProgress(userId, item.topic().getId(), topicItemId, now, item.generation());
        generations.require(userId, item.topic().getId(), item.generation());

        FurthestReachedVo furthest = savedItemAttempt.getFurthestReached();
        return TopicItemPositionResponse.builder()
                .topicItemId(topicItemId)
                .topicItemVersionId(savedItemAttempt.getTopicItemVersionId())
                .status(ItemProgressStatus.IN_PROGRESS)
                .stage(furthest == null ? null : furthest.getStage())
                .itemIndex(furthest == null ? null : furthest.getItemIndex())
                .lastAccessedAt(savedItemAttempt.getLastAccessedAt())
                .build();
    }

    // --- topic progress ----------------------------------------------------------------

    @Override
    public TopicProgressResponse getTopicProgress(GetTopicProgressRequest request) {
        String userId = requireUserId();
        String topicId = request.getTopicId();
        long generation = generation(userId, topicId);
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Topic " + topicId + " was not found"));
        TopicVersion release = resolvePublishedRelease(topic);
        List<TopicItemVersion> visibleItems = visibleItems(release);

        Map<String, UserTopicItemProgress> progressByItem = itemProgressByItem(userId, topicId, generation);
        Map<String, UserTopicItemAttempt> attemptsByItem = activeItemAttemptsByItem(userId, topicId, generation);

        List<TopicItemProgressResponse> items = new ArrayList<>();
        int completedItemCount = 0;
        boolean anyStarted = false;
        for (TopicItemVersion itemVersion : visibleItems) {
            String topicItemId = itemVersion.getTopicItemId();
            UserTopicItemProgress progress = progressByItem.get(topicItemId);
            UserTopicItemAttempt attempt = attemptsByItem.get(topicItemId);
            ItemProgressStatus status = resolveStatus(progress, attempt);
            if (status == ItemProgressStatus.COMPLETED) {
                completedItemCount++;
            }
            if (status != ItemProgressStatus.NOT_STARTED) {
                anyStarted = true;
            }
            items.add(TopicItemProgressResponse.builder()
                    .topicItemId(topicItemId)
                    .status(status)
                    .firstTryCorrectCount(progress == null ? null : progress.getFirstTryCorrectCount())
                    .totalQuizCount(progress == null ? null : progress.getTotalQuizCount())
                    .completedAt(progress == null ? null : progress.getCompletedAt())
                    .build());
        }

        int totalItemCount = visibleItems.size();
        ItemProgressStatus status;
        if (totalItemCount > 0 && completedItemCount == totalItemCount) {
            status = ItemProgressStatus.COMPLETED;
        } else if (anyStarted) {
            status = ItemProgressStatus.IN_PROGRESS;
        } else {
            status = ItemProgressStatus.NOT_STARTED;
        }

        UserTopicProgress topicProgress = findTopicProgress(userId, topicId, generation);
        return TopicProgressResponse.builder()
                .topicId(topicId)
                .status(status)
                .completedItemCount(completedItemCount)
                .totalItemCount(totalItemCount)
                .lastAccessedAt(topicProgress == null ? null : topicProgress.getLastAccessedAt())
                .items(items)
                .build();
    }

    // --- resolution --------------------------------------------------------------------

    /**
     * The item as it is published: its topic, the release that shows it and the item
     * versions of that release. Hidden items are not part of the visible sequence.
     */
    private ItemContext resolvePublishedItem(String topicItemId) {
        TopicItem item = topicItemRepository.findById(topicItemId)
                .orElseThrow(() -> new TopicItemNotFoundException(
                        "Topic item " + topicItemId + " was not found"));
        Topic topic = topicRepository.findById(item.getTopicId())
                .orElseThrow(() -> new TopicItemNotFoundException(
                        "Topic item " + topicItemId + " was not found"));
        TopicVersion release;
        try {
            release = resolvePublishedRelease(topic);
        } catch (TopicNotFoundException ex) {
            // An item of a topic that is not published is not visible to the learner.
            throw new TopicItemNotFoundException("Topic item " + topicItemId + " was not found");
        }
        List<TopicItemVersion> items = visibleItems(release);
        TopicItemVersion published = items.stream()
                .filter(candidate -> topicItemId.equals(candidate.getTopicItemId()))
                .findFirst()
                .orElseThrow(() -> new TopicItemNotFoundException(
                        "Topic item " + topicItemId + " was not found"));
        return new ItemContext(topic, release, published, items, generation(requireUserId(), topic.getId()));
    }

    private TopicVersion resolvePublishedRelease(Topic topic) {
        String releaseId = topic.getLatestPublishedVersion();
        if (releaseId == null || releaseId.isBlank()) {
            throw new TopicNotFoundException("Topic " + topic.getId() + " was not found");
        }
        TopicVersion release = topicVersionRepository.findById(releaseId)
                .orElseThrow(() -> new TopicNotFoundException("Topic " + topic.getId() + " was not found"));
        if (release.getStatus() != ContentStatus.PUBLISHED) {
            throw new TopicNotFoundException("Topic " + topic.getId() + " was not found");
        }
        return release;
    }

    /** Item versions of a release that the learner may see, in the order the topic lists them. */
    private List<TopicItemVersion> visibleItems(TopicVersion release) {
        if (release.getItemVersionIds() == null || release.getItemVersionIds().isEmpty()) {
            return List.of();
        }
        Map<String, TopicItemVersion> byId = new HashMap<>();
        for (TopicItemVersion itemVersion : topicItemVersionRepository.findAllById(
                new ArrayList<>(new LinkedHashSet<>(release.getItemVersionIds())))) {
            byId.put(itemVersion.getId(), itemVersion);
        }
        return byId.values().stream()
                .filter(itemVersion -> itemVersion.getStatus() != ContentStatus.HIDDEN)
                .sorted(ITEM_ORDER)
                .toList();
    }

    /**
     * The content version the learner works on: the version pinned by the running attempt,
     * or the published one. A learner never continues on a version a later release published.
     */
    private TopicItemVersion resolveLearningVersion(ItemContext item, UserTopicItemAttempt itemAttempt) {
        if (itemAttempt != null && itemAttempt.getTopicItemVersionId() != null) {
            Optional<TopicItemVersion> pinned = topicItemVersionRepository
                    .findById(itemAttempt.getTopicItemVersionId());
            if (pinned.isPresent()) {
                return pinned.get();
            }
        }
        return item.publishedVersion();
    }

    /**
     * The version a new item attempt freezes: the one the learning attempt publishes, so the
     * item attempt always points at a version of the release its attempt froze.
     */
    private TopicItemVersion resolveVersionForItemAttempt(ItemContext item, String topicItemId,
                                                           UserTopicAttempt attempt,
                                                           UserTopicItemAttempt existingItemAttempt) {
        if (existingItemAttempt != null && existingItemAttempt.getTopicItemVersionId() != null) {
            Optional<TopicItemVersion> pinned = topicItemVersionRepository
                    .findById(existingItemAttempt.getTopicItemVersionId());
            if (pinned.isPresent()) {
                return pinned.get();
            }
        }
        String releaseId = attempt == null ? null : attempt.getTopicVersionId();
        if (releaseId != null && !releaseId.equals(item.release().getId())) {
            TopicVersion pinnedRelease = topicVersionRepository.findById(releaseId).orElse(null);
            if (pinnedRelease != null) {
                TopicItemVersion version = visibleItems(pinnedRelease).stream()
                        .filter(candidate -> topicItemId.equals(candidate.getTopicItemId()))
                        .findFirst()
                        .orElse(null);
                if (version != null) {
                    return version;
                }
            }
        }
        return item.publishedVersion();
    }

    // --- preconditions -----------------------------------------------------------------

    private void requireLevelAllowed(Topic topic) {
        CefrLevel topicLevel = topic.getCefrLevel();
        CefrLevel learnerLevel = learnerLevelProvider.getCurrentLevel();
        if (topicLevel == null || learnerLevel == null) {
            return;
        }
        if (topicLevel.ordinal() > learnerLevel.ordinal()) {
            throw new LearningPreconditionFailedException(
                    "Topic level " + topicLevel + " is above the learner level " + learnerLevel);
        }
    }

    /** A topic holds at most one item being learned, so a new item cannot start beside it. */
    private void requireNoOtherItemInProgress(String userId, ItemContext item, String topicItemId) {
        for (UserTopicItemProgress progress : itemProgressByItem(userId, item.topic().getId(), item.generation()).values()) {
            if (progress.getStatus() == ItemProgressStatus.IN_PROGRESS
                    && !topicItemId.equals(progress.getTopicItemId())) {
                throw new LearningPreconditionFailedException(
                        "Topic item " + progress.getTopicItemId() + " of this topic is still being learned");
            }
        }
    }

    /** Items before the current one in the topic must be completed; hidden ones do not count. */
    private void requirePredecessorsCompleted(String userId, ItemContext item, String topicItemId) {
        Map<String, UserTopicItemProgress> progressByItem = itemProgressByItem(userId, item.topic().getId(), item.generation());
        for (TopicItemVersion candidate : item.visibleItems()) {
            if (topicItemId.equals(candidate.getTopicItemId())) {
                return;
            }
            UserTopicItemProgress progress = progressByItem.get(candidate.getTopicItemId());
            if (progress == null || progress.getStatus() != ItemProgressStatus.COMPLETED) {
                throw new LearningPreconditionFailedException(
                        "Topic item " + candidate.getTopicItemId() + " must be completed first");
            }
        }
    }

    // --- writes ------------------------------------------------------------------------

    /**
     * The learning attempt of the topic. One attempt is running per topic and per learner;
     * it belongs to the item the learner is working on and keeps the release it froze.
     */
    private UserTopicAttempt resolveLearningAttempt(String userId, ItemContext item, String topicItemId,
                                                     Instant now) {
        List<UserTopicAttempt> running = userTopicAttemptRepository
                .findByUserIdAndTopicIdAndAttemptTypeAndStatus(userId, item.topic().getId(),
                        AttemptType.LEARNING, AttemptStatus.IN_PROGRESS);
        UserTopicAttempt attempt = running.stream().filter(row -> row.getGeneration() == item.generation())
                .max(Comparator.comparing(UserTopicAttempt::getStartedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (attempt == null) {
            return userTopicAttemptRepository.save(UserTopicAttempt.builder()
                    .generation(item.generation())
                    .userId(userId)
                    .topicId(item.topic().getId())
                    .attemptType(AttemptType.LEARNING)
                    .scope(AttemptScope.ITEM)
                    .topicVersionId(item.release().getId())
                    .targetTopicItemId(topicItemId)
                    .currentTopicItemId(topicItemId)
                    .status(AttemptStatus.IN_PROGRESS)
                    .startedAt(now)
                    .lastAccessedAt(now)
                    .build());
        }
        if (!topicItemId.equals(attempt.getTargetTopicItemId())) {
            throw new LearningPreconditionFailedException(
                    "Another item of this topic is still being learned: " + attempt.getTargetTopicItemId());
        }
        attempt.setCurrentTopicItemId(topicItemId);
        attempt.setLastAccessedAt(now);
        return userTopicAttemptRepository.save(attempt);
    }

    /**
     * The item attempt holds the position of the learner. Its mark only moves forward:
     * looking back at earlier content must not pull the resume point behind.
     */
    private UserTopicItemAttempt upsertItemAttempt(String userId, UserTopicAttempt attempt,
                                                    String topicItemId, TopicItemVersion version,
                                                    LearningStage stage, int itemIndex, Instant now) {
        Optional<UserTopicItemAttempt> existing = userTopicItemAttemptRepository
                .findByUserTopicAttemptIdAndTopicItemId(attempt.getId(), topicItemId);
        UserTopicItemAttempt itemAttempt = existing.orElseGet(() -> UserTopicItemAttempt.builder()
                .userTopicAttemptId(attempt.getId())
                .generation(attempt.getGeneration())
                .userId(userId)
                .topicId(attempt.getTopicId())
                .topicItemId(topicItemId)
                .topicItemVersionId(version.getId())
                .status(AttemptStatus.IN_PROGRESS)
                .quizProgress(new ArrayList<>())
                .startedAt(now)
                .build());
        itemAttempt.setStatus(AttemptStatus.IN_PROGRESS);
        itemAttempt.setFurthestReached(advance(itemAttempt.getFurthestReached(), stage, itemIndex));
        itemAttempt.setLastAccessedAt(now);
        return userTopicItemAttemptRepository.save(itemAttempt);
    }

    private UserTopicItemProgress upsertItemProgress(String userId, String topicId, String topicItemId,
                                                     Instant now, long generation) {
        UserTopicItemProgress progress = findItemProgress(userId, topicItemId, generation);
        if (progress == null) {
            return userTopicItemProgressRepository.save(UserTopicItemProgress.builder()
                    .id(progressId(userId, topicId, "item-progress|" + topicItemId, generation))
                    .generation(generation)
                    .userId(userId)
                    .topicId(topicId)
                    .topicItemId(topicItemId)
                    .status(ItemProgressStatus.IN_PROGRESS)
                    .lastAccessedAt(now)
                    .build());
        }
        if (progress.getStatus() == ItemProgressStatus.NOT_STARTED) {
            progress.setStatus(ItemProgressStatus.IN_PROGRESS);
        }
        progress.setLastAccessedAt(now);
        return userTopicItemProgressRepository.save(progress);
    }

    private UserTopicProgress upsertTopicProgress(String userId, String topicId, String topicItemId,
                                                   Instant now, long generation) {
        UserTopicProgress topicProgress = findTopicProgress(userId, topicId, generation);
        if (topicProgress == null) topicProgress = UserTopicProgress.builder()
                .id(progressId(userId, topicId, "topic-progress", generation))
                .generation(generation).userId(userId).topicId(topicId).build();
        topicProgress.setLastTopicItemId(topicItemId);
        topicProgress.setLastAccessedAt(now);
        return userTopicProgressRepository.save(topicProgress);
    }

    // --- lookups -----------------------------------------------------------------------

    private UserTopicItemProgress findItemProgress(String userId, String topicItemId, long generation) {
        return userTopicItemProgressRepository.findByUserId(userId).stream()
                .filter(row -> topicItemId.equals(row.getTopicItemId()) && row.getGeneration() == generation).findFirst().orElse(null);
    }

    private Map<String, UserTopicItemProgress> itemProgressByItem(String userId, String topicId, long generation) {
        Map<String, UserTopicItemProgress> byItem = new HashMap<>();
        for (UserTopicItemProgress progress : userTopicItemProgressRepository.findByUserIdAndTopicId(userId, topicId)) {
            if (progress.getGeneration() == generation) byItem.put(progress.getTopicItemId(), progress);
        }
        return byItem;
    }

    private Map<String, UserTopicItemAttempt> activeItemAttemptsByItem(String userId, String topicId, long generation) {
        Map<String, UserTopicItemAttempt> byItem = new HashMap<>();
        for (UserTopicItemAttempt attempt : userTopicItemAttemptRepository.findByUserIdAndTopicId(userId, topicId)) {
            if (attempt.getGeneration() != generation || attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
                continue;
            }
            UserTopicItemAttempt known = byItem.get(attempt.getTopicItemId());
            if (known == null || LAST_ACCESSED.compare(attempt, known) > 0) {
                byItem.put(attempt.getTopicItemId(), attempt);
            }
        }
        return byItem;
    }

    /** The running item attempt of an item, the source of truth for the position. */
    private UserTopicItemAttempt findActiveItemAttempt(String userId, String topicItemId, long generation) {
        List<UserTopicItemAttempt> attempts = userTopicItemAttemptRepository
                .findByUserIdAndTopicItemId(userId, topicItemId);
        if (attempts == null || attempts.isEmpty()) {
            return null;
        }
        return attempts.stream()
                .filter(attempt -> attempt.getGeneration() == generation && attempt.getStatus() == AttemptStatus.IN_PROGRESS)
                .max(LAST_ACCESSED)
                .orElse(null);
    }

    private long generation(String userId, String topicId) { return generations.current(userId, topicId); }
    private String progressId(String userId, String topicId, String kind, long generation) {
        return generation == 0 ? null : generations.id(userId, topicId + "|" + kind + "|" + generation);
    }
    private UserTopicProgress findTopicProgress(String userId, String topicId, long generation) {
        return userTopicProgressRepository.findByUserId(userId).stream()
                .filter(row -> topicId.equals(row.getTopicId()) && row.getGeneration() == generation).findFirst().orElse(null);
    }

    // --- guards ------------------------------------------------------------------------

    private String requireUserId() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new LearningPreconditionFailedException("Sign in to learn a topic item");
        }
        return userId.trim();
    }

    // --- helpers -----------------------------------------------------------------------

    /** The mark of the learner only moves forward, so a later stage or index wins. */
    private FurthestReachedVo advance(FurthestReachedVo current, LearningStage stage, int itemIndex) {
        if (current == null || current.getStage() == null) {
            return FurthestReachedVo.builder().stage(stage).itemIndex(itemIndex).build();
        }
        int currentIndex = current.getItemIndex() == null ? -1 : current.getItemIndex();
        if (current.getStage().ordinal() > stage.ordinal()
                || (current.getStage().ordinal() == stage.ordinal() && currentIndex >= itemIndex)) {
            return current;
        }
        return FurthestReachedVo.builder().stage(stage).itemIndex(itemIndex).build();
    }

    /** A completed item keeps its result; otherwise a running attempt means it is in progress. */
    private ItemProgressStatus resolveStatus(UserTopicItemProgress progress,
                                              UserTopicItemAttempt itemAttempt) {
        if (progress != null && progress.getStatus() == ItemProgressStatus.COMPLETED) {
            return ItemProgressStatus.COMPLETED;
        }
        if (progress != null && progress.getStatus() == ItemProgressStatus.IN_PROGRESS) {
            return ItemProgressStatus.IN_PROGRESS;
        }
        if (itemAttempt != null) {
            return ItemProgressStatus.IN_PROGRESS;
        }
        return ItemProgressStatus.NOT_STARTED;
    }

    /** The published item, the release that shows it and the visible sequence of that release. */
    private record ItemContext(Topic topic,
                                TopicVersion release,
                                TopicItemVersion publishedVersion,
                                List<TopicItemVersion> visibleItems, long generation) {
    }
}
