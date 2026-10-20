package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.topic.dto.response.TopicDetailResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemSummaryResponse;
import fit.iuh.engolearn.topic.dto.response.TopicPageResponse;
import fit.iuh.engolearn.topic.dto.response.TopicSummaryResponse;
import fit.iuh.engolearn.topic.exception.InvalidBrowseRequestException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.model.Topic;
import fit.iuh.engolearn.topic.model.TopicItemVersion;
import fit.iuh.engolearn.topic.model.TopicVersion;
import fit.iuh.engolearn.topic.model.enums.ContentStatus;
import fit.iuh.engolearn.topic.model.vo.ExpressionBlockVo;
import fit.iuh.engolearn.topic.model.vo.ExpressionPurposeVo;
import fit.iuh.engolearn.topic.repository.TopicItemVersionRepository;
import fit.iuh.engolearn.topic.repository.TopicRepository;
import fit.iuh.engolearn.topic.repository.TopicVersionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class TopicBrowseServiceImpl implements TopicBrowseService {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private static final Comparator<TopicItemVersion> ITEM_ORDER =
            Comparator.comparing(TopicItemVersion::getOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TopicItemVersion::getTopicItemId, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TopicItemVersion::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final TopicRepository topicRepository;
    private final TopicVersionRepository topicVersionRepository;
    private final TopicItemVersionRepository topicItemVersionRepository;

    public TopicBrowseServiceImpl(TopicRepository topicRepository,
                                  TopicVersionRepository topicVersionRepository,
                                  TopicItemVersionRepository topicItemVersionRepository) {
        this.topicRepository = topicRepository;
        this.topicVersionRepository = topicVersionRepository;
        this.topicItemVersionRepository = topicItemVersionRepository;
    }

    @Override
    public TopicPageResponse getAvailableTopics(GetAvailableTopicRequest request) {
        if (request == null) {
            throw new InvalidBrowseRequestException("Request is required");
        }
        int limit = resolveLimit(request.getLimit());
        CefrLevel level = resolveLevel(request.getLevel());
        TopicSortOrder sort = resolveSort(request.getSort());
        String keyword = normalizeKeyword(request.getKeyword());
        String fingerprint = TopicCursorCodec.fingerprint(level, keyword);

        Integer cursorSortKey = null;
        String cursorId = null;
        if (request.getCursor() != null && !request.getCursor().isBlank()) {
            TopicCursorCodec.DecodedCursor cursor = TopicCursorCodec.decode(request.getCursor());
            if (cursor.sort() != sort) {
                throw new InvalidBrowseRequestException("Cursor does not belong to the requested sort order");
            }
            if (!cursor.filterFingerprint().equals(fingerprint)) {
                throw new InvalidBrowseRequestException("Cursor does not belong to the requested filters");
            }
            cursorSortKey = cursor.sortKey();
            cursorId = cursor.topicId();
        }

        // One extra row tells whether a further page exists without a second count query.
        List<Topic> rows = topicRepository.findPublishedPage(level, keyword, sortKeyOf(sort),
                cursorSortKey, cursorId, limit + 1);
        boolean hasMore = rows.size() > limit;

        ResolvedPage resolved = resolvePage(rows);
        List<Topic> page = resolved.listed().size() > limit
                ? resolved.listed().subList(0, limit)
                : resolved.listed();

        Map<String, Counts> countsByTopic = resolved.countsByTopic();
        List<TopicSummaryResponse> items = page.stream()
                .map(topic -> toSummary(topic, countsByTopic.getOrDefault(topic.getId(), Counts.EMPTY)))
                .toList();

        String nextCursor = null;
        if (hasMore) {
            // The cursor points at a row the page moved past, so scrolling cannot stall and
            // cannot repeat a topic, not even when a row was dropped from the page.
            Topic cursorRow = page.isEmpty() ? rows.get(limit - 1) : page.get(page.size() - 1);
            nextCursor = TopicCursorCodec.encode(sort, fingerprint, sortKeyOf(cursorRow, sort), cursorRow.getId());
        }
        return TopicPageResponse.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Override
    public TopicDetailResponse getTopicDetail(GetTopicDetailRequest request) {
        if (request == null || request.getTopicId() == null || request.getTopicId().isBlank()) {
            throw new InvalidBrowseRequestException("Topic id is required");
        }
        String topicId = request.getTopicId().trim();
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Topic " + topicId + " was not found"));
        String releaseId = topic.getLatestPublishedVersion();
        if (releaseId == null || releaseId.isBlank()) {
            throw new TopicNotFoundException("Topic " + topicId + " was not found");
        }
        TopicVersion release = topicVersionRepository.findById(releaseId)
                .orElseThrow(() -> new TopicNotFoundException("Topic " + topicId + " was not found"));
        if (release.getStatus() != ContentStatus.PUBLISHED) {
            // A release that is not published is treated exactly like a missing topic, so the
            // response never reveals content of an unpublished release.
            throw new TopicNotFoundException("Topic " + topicId + " was not found");
        }

        List<TopicItemVersion> itemVersions = resolveItemVersions(release);
        List<TopicItemSummaryResponse> items = itemVersions.stream()
                .filter(itemVersion -> itemVersion.getStatus() != ContentStatus.HIDDEN)
                .sorted(ITEM_ORDER)
                .map(this::toItemSummary)
                .toList();

        return TopicDetailResponse.builder()
                .topicId(topic.getId())
                .titleEn(topic.getTitleEn())
                .titleVi(topic.getTitleVi())
                .cefrLevel(topic.getCefrLevel())
                .description(topic.getDescription())
                .items(items)
                .build();
    }

    // Resolves the releases and visible-item counts of the fetched rows
    private ResolvedPage resolvePage(List<Topic> rows) {
        if (rows.isEmpty()) {
            return new ResolvedPage(List.of(), Map.of());
        }
        List<String> releaseIds = rows.stream()
                .map(Topic::getLatestPublishedVersion)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, TopicVersion> releases = new HashMap<>();
        if (!releaseIds.isEmpty()) {
            for (TopicVersion release : topicVersionRepository.findAllById(releaseIds)) {
                releases.put(release.getId(), release);
            }
        }

        LinkedHashSet<String> itemVersionIds = new LinkedHashSet<>();
        for (TopicVersion release : releases.values()) {
            if (release.getItemVersionIds() != null) {
                itemVersionIds.addAll(release.getItemVersionIds());
            }
        }
        Map<String, TopicItemVersion> itemVersions = new HashMap<>();
        if (!itemVersionIds.isEmpty()) {
            for (TopicItemVersion itemVersion : topicItemVersionRepository.findAllById(new ArrayList<>(itemVersionIds))) {
                itemVersions.put(itemVersion.getId(), itemVersion);
            }
        }

        List<Topic> listed = new ArrayList<>(rows.size());
        Map<String, Counts> countsByTopic = new HashMap<>();
        for (Topic topic : rows) {
            TopicVersion release = releases.get(topic.getLatestPublishedVersion());
            if (release == null || release.getStatus() != ContentStatus.PUBLISHED) {
                continue;
            }
            int vocabularyCount = 0;
            int quizCount = 0;
            if (release.getItemVersionIds() != null) {
                for (String itemVersionId : release.getItemVersionIds()) {
                    TopicItemVersion itemVersion = itemVersions.get(itemVersionId);
                    if (itemVersion == null || itemVersion.getStatus() == ContentStatus.HIDDEN) {
                        continue;
                    }
                    vocabularyCount += sizeOf(itemVersion.getVocabularies());
                    quizCount += sizeOf(itemVersion.getQuizzes());
                }
            }
            listed.add(topic);
            countsByTopic.put(topic.getId(), new Counts(vocabularyCount, quizCount));
        }
        return new ResolvedPage(listed, countsByTopic);
    }

    /**
     * Item versions of a release, resolved by the manifest ids. Manifest ids that no longer
     * resolve are skipped, so the detail of a release never shows an item that is not part of
     * it.
     */
    private List<TopicItemVersion> resolveItemVersions(TopicVersion release) {
        if (release.getItemVersionIds() == null || release.getItemVersionIds().isEmpty()) {
            return List.of();
        }
        Map<String, TopicItemVersion> byId = new HashMap<>();
        for (TopicItemVersion itemVersion : topicItemVersionRepository.findAllById(
                new ArrayList<>(new LinkedHashSet<>(release.getItemVersionIds())))) {
            byId.put(itemVersion.getId(), itemVersion);
        }
        return new ArrayList<>(byId.values());
    }

    private TopicSummaryResponse toSummary(Topic topic, Counts counts) {
        return TopicSummaryResponse.builder()
                .topicId(topic.getId())
                .coverUrl(topic.getCoverUrl())
                .titleEn(topic.getTitleEn())
                .titleVi(topic.getTitleVi())
                .cefrLevel(topic.getCefrLevel())
                .vocabularyCount(counts.vocabularyCount())
                .quizCount(counts.quizCount())
                // Maintained on the topic document, the same value the duration sort key uses.
                .durationMinutes(orZero(topic.getDurationMinutes()))
                .build();
    }

    private TopicItemSummaryResponse toItemSummary(TopicItemVersion itemVersion) {
        return TopicItemSummaryResponse.builder()
                .topicItemId(itemVersion.getTopicItemId())
                .title(itemVersion.getTitle())
                .description(itemVersion.getDescription())
                .durationMinutes(orZero(itemVersion.getDurationMinutes()))
                .vocabularyCount(sizeOf(itemVersion.getVocabularies()))
                .expressionCount(countExpressions(itemVersion))
                .quizCount(sizeOf(itemVersion.getQuizzes()))
                .build();
    }

    private static int countExpressions(TopicItemVersion itemVersion) {
        if (itemVersion.getExpressions() == null) {
            return 0;
        }
        int total = 0;
        for (ExpressionBlockVo block : itemVersion.getExpressions()) {
            if (block.getPurposes() == null) {
                continue;
            }
            for (ExpressionPurposeVo purpose : block.getPurposes()) {
                total += sizeOf(purpose.getExamples());
            }
        }
        return total;
    }

    private static TopicRepository.SortKey sortKeyOf(TopicSortOrder sort) {
        return sort == TopicSortOrder.DURATION
                ? TopicRepository.SortKey.DURATION_MINUTES
                : TopicRepository.SortKey.ORDER_INDEX;
    }

    private static Integer sortKeyOf(Topic topic, TopicSortOrder sort) {
        return sort == TopicSortOrder.DURATION ? topic.getDurationMinutes() : topic.getOrderIndex();
    }

    private static int resolveLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw new InvalidBrowseRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return requested;
    }

    private static CefrLevel resolveLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CefrLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidBrowseRequestException("level is not a CEFR level: " + raw);
        }
    }

    private static TopicSortOrder resolveSort(String raw) {
        return TopicSortOrder.fromApiValue(raw)
                .orElseThrow(() -> new InvalidBrowseRequestException("sort is not a supported sort order: " + raw));
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** Visible-item counts of one topic card. */
    private record Counts(int vocabularyCount, int quizCount) {

        private static final Counts EMPTY = new Counts(0, 0);
    }

    /** Rows of one fetched batch that can be listed, with their visible-item counts. */
    private record ResolvedPage(List<Topic> listed, Map<String, Counts> countsByTopic) {
    }
}
