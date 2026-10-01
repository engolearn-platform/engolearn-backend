package fit.iuh.engolearn.learning.progress.service;

import fit.iuh.engolearn.content.api.TopicCardView;
import fit.iuh.engolearn.content.api.TopicDetailView;
import fit.iuh.engolearn.content.api.TopicFilter;
import fit.iuh.engolearn.content.api.TopicItemDetailView;
import fit.iuh.engolearn.content.api.TopicQuery;
import fit.iuh.engolearn.learning.progress.dto.ExploreTopicItemResponse;
import fit.iuh.engolearn.learning.progress.dto.TopicDetailWithProgressResponse;
import fit.iuh.engolearn.learning.progress.model.ItemProgressStatus;
import fit.iuh.engolearn.learning.progress.model.UserTopicItemProgress;
import fit.iuh.engolearn.learning.progress.model.UserTopicProgress;
import fit.iuh.engolearn.learning.progress.repository.UserTopicItemProgressRepository;
import fit.iuh.engolearn.learning.progress.repository.UserTopicProgressRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ExploreService {

    private final TopicQuery topicQuery;
    private final UserTopicProgressRepository topicProgress;
    private final UserTopicItemProgressRepository itemProgress;

    public ExploreService(
            TopicQuery topicQuery,
            UserTopicProgressRepository topicProgress,
            UserTopicItemProgressRepository itemProgress) {
        this.topicQuery = topicQuery;
        this.topicProgress = topicProgress;
        this.itemProgress = itemProgress;
    }

    public Page<ExploreTopicItemResponse> explore(
            TopicFilter filter, String userIdOrNull, Pageable pageable) {
        Page<TopicCardView> cards = topicQuery.findPublishedTopics(filter, pageable);
        if (userIdOrNull == null || userIdOrNull.isBlank()) {
            return cards.map(card -> new ExploreTopicItemResponse(card, null));
        }
        List<String> topicIds = cards.map(TopicCardView::topicId).toList();
        Map<String, UserTopicProgress> progressByTopic =
                topicProgress.findByUserIdAndTopicIdIn(userIdOrNull, topicIds).stream()
                        .collect(Collectors.toMap(UserTopicProgress::getTopicId, Function.identity()));
        Map<String, Integer> versionById =
                topicQuery.findVersionNumbers(
                        progressByTopic.values().stream()
                                .map(UserTopicProgress::getTopicVersionId)
                                .filter(id -> id != null && !id.isBlank())
                                .collect(Collectors.toSet()));
        return cards.map(
                card -> {
                    UserTopicProgress progress = progressByTopic.get(card.topicId());
                    return new ExploreTopicItemResponse(
                            card,
                            progress == null ? null : toProgressView(card, progress, versionById));
                });
    }

    public Optional<TopicDetailWithProgressResponse> getDetail(String topicId, String userIdOrNull) {
        return getDetail(topicId, null, userIdOrNull);
    }

    public Optional<TopicDetailWithProgressResponse> getDetail(
            String topicId, Integer versionOrNull, String userIdOrNull) {
        Optional<TopicDetailView> detail = topicQuery.getPublishedTopicDetail(topicId, versionOrNull);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        TopicDetailView view = detail.get();
        if (userIdOrNull == null || userIdOrNull.isBlank()) {
            List<TopicDetailWithProgressResponse.ItemProgressView> items =
                    view.items().stream()
                            .map(item -> new TopicDetailWithProgressResponse.ItemProgressView(item, null, null, null, null, null))
                            .toList();
            return Optional.of(new TopicDetailWithProgressResponse(view, null, items));
        }
        Optional<UserTopicProgress> progress = topicProgress.findByUserIdAndTopicId(userIdOrNull, topicId);
        Map<String, Integer> versionById =
                progress.map(UserTopicProgress::getTopicVersionId)
                        .filter(id -> id != null && !id.isBlank())
                        .map(id -> topicQuery.findVersionNumbers(List.of(id)))
                        .orElseGet(Map::of);
        List<String> itemIds = view.items().stream().map(TopicItemDetailView::topicItemId).toList();
        Map<String, UserTopicItemProgress> itemProgressById =
                itemProgress.findByUserIdAndTopicItemIdIn(userIdOrNull, itemIds).stream()
                        .collect(
                                Collectors.toMap(
                                        UserTopicItemProgress::getTopicItemId,
                                        Function.identity(),
                                        (a, b) -> a));
        long completed =
                itemProgressById.values().stream()
                        .filter(p -> p.getStatus() == ItemProgressStatus.COMPLETED)
                        .count();
        double percent =
                view.items().isEmpty() ? 0.0 : (completed * 100.0) / view.items().size();
        String continueItemId =
                progress.map(UserTopicProgress::getLastTopicItemId).orElseGet(() -> firstIncomplete(view, itemProgressById));
        boolean outdated =
                progress.map(
                                p -> {
                                    Integer userVersion = resolveUserVersion(p, versionById);
                                    return view.version() != null
                                            && userVersion != null
                                            && userVersion < view.version();
                                })
                        .orElse(false);
        boolean completedTopic =
                progress.map(p -> Boolean.TRUE.equals(p.getIsCompleted())).orElse(false);
        TopicDetailWithProgressResponse.TopicProgressSummary summary =
                new TopicDetailWithProgressResponse.TopicProgressSummary(
                        round1(percent),
                        completedTopic,
                        continueItemId,
                        outdated,
                        progress.map(UserTopicProgress::getLastAccessedAt).orElse(null));
        List<TopicDetailWithProgressResponse.ItemProgressView> items =
                view.items().stream()
                        .map(
                                item -> {
                                    UserTopicItemProgress ip = itemProgressById.get(item.topicItemId());
                                    return new TopicDetailWithProgressResponse.ItemProgressView(
                                            item,
                                            ip == null || ip.getStatus() == null
                                                    ? null
                                                    : ip.getStatus().name(),
                                            ip == null || ip.getFurthestReach() == null
                                                    ? null
                                                    : ip.getFurthestReach().getStage(),
                                            ip == null || ip.getFurthestReach() == null
                                                    ? null
                                                    : ip.getFurthestReach().getStep(),
                                            ip == null ? null : ip.getSpeakingScore(),
                                            ip == null ? null : ip.getCompletedAt());
                                })
                        .toList();
        return Optional.of(new TopicDetailWithProgressResponse(view, summary, items));
    }

    private ExploreTopicItemResponse.TopicProgressView toProgressView(
            TopicCardView card, UserTopicProgress progress, Map<String, Integer> versionById) {
        int total = Math.max(card.totalItems(), 0);
        Double percent = null;
        if (total > 0) {
            percent = round1(estimatePercent(card, progress));
        }
        Integer userVersion = resolveUserVersion(progress, versionById);
        boolean outdated =
                card.publishedVersion() != null
                        && userVersion != null
                        && userVersion < card.publishedVersion();
        return new ExploreTopicItemResponse.TopicProgressView(
                percent,
                progress.getIsCompleted(),
                progress.getLastTopicItemId(),
                outdated,
                userVersion,
                progress.getLastAccessedAt());
    }

    private double estimatePercent(TopicCardView card, UserTopicProgress progress) {
        if (Boolean.TRUE.equals(progress.getIsCompleted())) {
            return 100.0;
        }
        if (progress.getLastTopicItemId() == null || card.totalItems() == 0) {
            return 0.0;
        }
        return 0.0;
    }

    private String firstIncomplete(
            TopicDetailView view, Map<String, UserTopicItemProgress> byId) {
        return view.items().stream()
                .filter(
                        item -> {
                            UserTopicItemProgress p = byId.get(item.topicItemId());
                            return p == null || p.getStatus() != ItemProgressStatus.COMPLETED;
                        })
                .map(TopicItemDetailView::topicItemId)
                .findFirst()
                .orElse(null);
    }

    private Integer resolveUserVersion(UserTopicProgress progress, Map<String, Integer> versionById) {
        if (progress.getTopicVersionId() == null || progress.getTopicVersionId().isBlank()) {
            return null;
        }
        return versionById.get(progress.getTopicVersionId());
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
