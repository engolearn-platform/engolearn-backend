package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.request.TopicManagementListQuery;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemDetail;
import fit.iuh.engolearn.topic.dto.response.TopicManagementItemSummary;
import fit.iuh.engolearn.topic.dto.response.TopicManagementListResponse;
import fit.iuh.engolearn.topic.dto.response.TopicManagementResponse;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.mapper.TopicManagementItemMapper;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItem;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;
import fit.iuh.engolearn.topic.repository.TopicItemRepository;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicManagementQueries;
import fit.iuh.engolearn.topic.repository.TopicManagementQueries.ItemStats;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import fit.iuh.engolearn.topic.service.TopicManagementReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTENT_MANAGER')")
public class TopicManagementReadServiceImpl implements TopicManagementReadService {
    private static final Comparator<Integer> VERSION_NUMBER = Comparator.nullsFirst(Comparator.naturalOrder());
    private static final Comparator<TopicItemVersion> VERSION_ORDER = Comparator
            .comparing(TopicItemVersion::getVersion, VERSION_NUMBER)
            .thenComparing(TopicItemVersion::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TopicItemVersion::getId, Comparator.naturalOrder());
    private static final Comparator<TopicItemVersion> ITEM_ORDER = Comparator
            .comparing(TopicItemVersion::getOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TopicItemVersion::getTopicItemId);

    private final TopicRepository topics;
    private final TopicItemRepository topicItems;
    private final TopicVersionRepository releases;
    private final TopicItemVersionRepository versions;
    private final TopicManagementQueries queries;
    private final TopicManagementItemMapper itemMapper;

    @Override
    public TopicManagementListResponse list(TopicManagementListQuery query) {
        var result = queries.findPage(query.search(), enumValue(query.cefr(), CefrLevel.class),
                enumValue(query.category(), TopicCategory.class),
                query.status() == null ? null : query.status().equals("published"), query.page(), query.size());
        var stats = queries.itemStats(result.getContent().stream().map(Topic::getId).toList());
        var rows = result.getContent().stream().map(topic -> new TopicManagementListResponse.Item(
                topic.getId(), topic.getTitleEn(), topic.getTitleVi(), topic.getCefrLevel(), topic.getCategory(),
                publicationLabel(topic), stats.getOrDefault(topic.getId(), ItemStats.EMPTY).count(), topic.getUpdatedAt())).toList();
        long published = queries.countByPublication(true);
        long draft = queries.countByPublication(false);
        return new TopicManagementListResponse(rows, query.page(), query.size(), result.getTotalElements(), result.getTotalPages(),
                new TopicManagementListResponse.Counters(published + draft, published, draft));
    }

    @Override
    public TopicManagementResponse topic(String topicId) {
        Topic topic = requireTopic(topicId);
        ItemStats stats = queries.itemStats(List.of(topicId)).getOrDefault(topicId, ItemStats.EMPTY);
        var publication = publicationVersion(topic).map(release -> new TopicManagementResponse.PublicationVersion(
                release.getId(), release.getVersion(), release.getStatus(), release.getPreviousVersionId(), release.getPublishedAt()))
                .orElse(null);
        return new TopicManagementResponse(topic.getId(), topic.getTitleEn(), topic.getTitleVi(), topic.getCefrLevel(),
                topic.getCategory(), topic.getDescription(), topic.getCoverUrl(), publicationLabel(topic), stats.duration(),
                stats.count(), topic.getCreatedAt(), topic.getUpdatedAt(), topic.getTags(), topic.getOrderIndex(),
                topic.getLatestPublishedVersion(), topic.getLatestBetaVersion(), publication);
    }

    @Override
    public List<TopicManagementItemSummary> items(String topicId) {
        Topic topic = requireTopic(topicId);
        Set<String> manifest = manifest(topic);
        var byItem = versions.findManagementMetadata(List.of(topicId)).stream()
                .filter(version -> version.getTopicItemId() != null)
                .collect(Collectors.groupingBy(TopicItemVersion::getTopicItemId));
        return topicItems.findAllByTopicId(topicId).stream()
                .map(item -> select(item, byItem.getOrDefault(item.getId(), List.of()), manifest))
                .sorted(ITEM_ORDER).map(itemMapper::summary).toList();
    }

    @Override
    public TopicManagementItemDetail item(String topicId, String itemId) {
        Topic topic = requireTopic(topicId);
        TopicItem item = topicItems.findByIdAndTopicId(itemId, topicId)
                .orElseThrow(() -> new TopicItemNotFoundException("Topic item was not found"));
        TopicItemVersion selected = select(item, versions.findManagementMetadataForItem(topicId, itemId), manifest(topic));
        if (selected.getId() == null) return itemMapper.detail(selected);
        return itemMapper.detail(versions.findByIdAndTopicIdAndTopicItemId(selected.getId(), topicId, itemId)
                .orElseThrow(() -> new TopicItemNotFoundException("Topic item version was not found")));
    }

    private Topic requireTopic(String topicId) {
        return topics.findById(topicId).orElseThrow(() -> new TopicNotFoundException("Topic was not found"));
    }

    private Optional<TopicVersion> publicationVersion(Topic topic) {
        if (!hasPublicationPointer(topic)) return Optional.empty();
        return releases.findById(topic.getLatestPublishedVersion())
                .filter(release -> Objects.equals(release.getTopicId(), topic.getId()));
    }

    private Set<String> manifest(Topic topic) {
        return publicationVersion(topic).map(TopicVersion::getItemVersionIds)
                .<Set<String>>map(HashSet::new).orElseGet(Set::of);
    }

    private static TopicItemVersion select(TopicItem item, List<TopicItemVersion> history, Set<String> manifest) {
        Optional<TopicItemVersion> released = history.stream().filter(version -> manifest.contains(version.getId()))
                .max(VERSION_ORDER);
        // Honor explicit working/visibility states at or beyond the release, regardless of newer BETA/history records.
        return history.stream().filter(version -> version.getStatus() == ContentStatus.DRAFT
                        || version.getStatus() == ContentStatus.HIDDEN)
                .filter(version -> released.isEmpty()
                        || VERSION_NUMBER.compare(version.getVersion(), released.get().getVersion()) >= 0)
                .max(VERSION_ORDER).or(() -> released)
                .orElseGet(() -> TopicItemVersion.builder().topicItemId(item.getId()).topicId(item.getTopicId()).build());
    }

    private static boolean hasPublicationPointer(Topic topic) {
        return topic.getLatestPublishedVersion() != null && !topic.getLatestPublishedVersion().isEmpty();
    }

    private static String publicationLabel(Topic topic) {
        return hasPublicationPointer(topic) ? "published" : "draft";
    }

    private static <T extends Enum<T>> T enumValue(String value, Class<T> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
