package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.user.contract.UserLevelLookup;
import fit.iuh.engolearn.topic.service.*;
import fit.iuh.engolearn.topic.utils.TopicCursorCodec;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.contract.TopicSpeakingHistory;
import fit.iuh.engolearn.topic.contract.TopicCompanionReads;
import fit.iuh.engolearn.topic.contract.exception.TopicCompanionInvalidQueryException;
import fit.iuh.engolearn.topic.contract.dto.*;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingHistoryDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingHistoryDto.LearningStatus;
import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.exception.InvalidBrowseRequestException;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.mapper.TopicRetrySessionMapper;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TopicLearnerCatalogServiceImpl implements TopicLearnerCatalogService, TopicSpeakingHistory, TopicCompanionReads {
    private final TopicBrowseService browse;
    private final UserLevelLookup userLevels;
    private final UserTopicProgressRepository topics;
    private final UserTopicItemProgressRepository items;
    private final UserTopicItemAttemptRepository attempts;
    private final UserTopicAttemptRepository sessions;
    private final TopicGenerationService generations;
    private final TopicRetryRuntimeRepository retryRuntimes;
    private final TopicRetrySessionMapper retrySessionMapper;
    private final TopicRepository topicRepository;
    private final TopicVersionRepository versions;
    private final TopicItemVersionRepository itemVersions;
    private final TopicMediaService media;

    @Override
    public TopicPageResponse discover(String userId, GetAvailableTopicRequest request) {
        if (userId == null) return browse.getAvailableTopics(request);
        CefrLevel learnerLevel = TopicUserLevelResolver.require(userLevels, userId);
        State state = state(userId);
        List<TopicSummaryResponse> rows = readAll(request);
        rows.forEach(row -> enrich(row, state, learnerLevel));
        rows = rows.stream().filter(row -> !state.owned().containsKey(row.getTopicId()) &&
                row.getProgress().status().equals("NOT_STARTED")).toList();
        String fingerprint = fingerprint(request, userId);
        List<TopicSummaryResponse> after = rows;
        if (request.getCursor() != null && !request.getCursor().isBlank()) {
            TopicCursorCodec.DecodedCursor cursor = TopicCursorCodec.decode(request.getCursor());
            TopicSortOrder sort = sort(request);
            if (cursor.sort() != sort || !fingerprint.equals(cursor.filterFingerprint())) {
                throw new InvalidBrowseRequestException("Cursor does not belong to this viewer and filter");
            }
            after = rows.stream().filter(row -> after(row, cursor, sort)).toList();
        }
        int limit = limit(request.getLimit());
        boolean more = after.size() > limit;
        List<TopicSummaryResponse> page = after.subList(0, Math.min(limit, after.size()));
        String cursor = more ? cursor(page.get(page.size() - 1), request, fingerprint) : null;
        return TopicPageResponse.builder().items(page).total(rows.size()).hasMore(more).nextCursor(cursor).build();
    }

    @Override
    public MyTopicPageResponse mine(String userId, String status, Integer requestedLimit, String cursor) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        if (!Set.of("IN_PROGRESS", "COMPLETED").contains(status)) throw new InvalidBrowseRequestException("Invalid topic status");
        CefrLevel learnerLevel = TopicUserLevelResolver.require(userLevels, userId);
        State state = state(userId);
        List<TopicSummaryResponse> owned = readAll(GetAvailableTopicRequest.builder().build());
        owned.forEach(row -> enrich(row, state, learnerLevel));
        owned = owned.stream().filter(row -> !row.getProgress().status().equals("NOT_STARTED") || state.owned().containsKey(row.getTopicId())).toList();
        long completed = owned.stream().filter(row -> row.getProgress().status().equals("COMPLETED")).count();
        List<TopicSummaryResponse> rows = owned.stream().filter(row -> row.getProgress().status().equals(status))
                .sorted(Comparator.comparing(TopicSummaryResponse::getLastAccessedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(TopicSummaryResponse::getTopicId)).toList();
        long total = rows.size();
        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
                String prefix = userId + "|" + status + "|";
                if (!decoded.startsWith(prefix)) throw new IllegalArgumentException();
                String lastId = decoded.substring(prefix.length());
                int index = -1;
                for (int i = 0; i < rows.size(); i++) if (rows.get(i).getTopicId().equals(lastId)) index = i;
                if (index < 0) throw new IllegalArgumentException();
                rows = rows.subList(index + 1, rows.size());
            } catch (IllegalArgumentException error) { throw new InvalidBrowseRequestException("Invalid personal cursor"); }
        }
        int limit = limit(requestedLimit);
        boolean more = rows.size() > limit;
        List<TopicSummaryResponse> page = rows.subList(0, Math.min(limit, rows.size()));
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                (userId + "|" + status + "|" + page.get(page.size() - 1).getTopicId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : null;
        return new MyTopicPageResponse(page, total, new MyTopicPageResponse.Counts(owned.size() - completed, completed), next, more);
    }

    @Override
    public TopicDetailResponse detail(String userId, String topicId) {
        TopicDetailResponse detail = browse.getTopicDetail(GetTopicDetailRequest.builder().topicId(topicId).build());

        if (userId == null) {
            detail.setResetAvailability(new TopicCatalogPolicy.Availability(false, "SIGN_IN_REQUIRED"));
            return detail;
        }
        CefrLevel learnerLevel = TopicUserLevelResolver.require(userLevels, userId);
        State state = state(userId);
        detail.setGeneration(state.generations().getOrDefault(topicId, 0L));
        Map<String, String> visible = new LinkedHashMap<>();
        detail.getItems().forEach(item -> visible.put(item.getTopicItemId(), item.getPublishedVersionId()));
        detail.setProgress(progress(topicId, visible, state));
        detail.setHasNewContent(hasNew(visible, state));
        boolean previousDone = true;
        boolean anotherRunning = state.running().values().stream().anyMatch(a -> topicId.equals(a.getTopicId()) && visible.containsKey(a.getTopicItemId()));
        TopicRetryRuntime retry = state.retries().get(topicId);
        boolean retryActive = retry != null && retry.getStatus() == RetryStatus.IN_PROGRESS;
        for (TopicItemSummaryResponse item : detail.getItems()) {
            UserTopicItemProgress stored = state.items().get(item.getTopicItemId());
            UserTopicItemAttempt running = state.running().get(item.getTopicItemId());
            boolean completed = stored != null && stored.getStatus() == ItemProgressStatus.COMPLETED;
            item.setLearningStatus(completed ? "COMPLETED" : running != null || stored != null && stored.getStatus() == ItemProgressStatus.IN_PROGRESS ? "IN_PROGRESS" : "NOT_STARTED");
            item.setHasNewContent(completed && !Objects.equals(stored.getCompletedTopicItemVersionId(), item.getPublishedVersionId()));
            item.setCompletedAttemptId(stored == null ? null : stored.getLastCompletedAttemptId());
            if (running != null && running.getFurthestReached() != null) {
                item.setLearningAttempt(new TopicCatalogPolicy.AttemptReference(running.getId(), new TopicCatalogPolicy.Position(
                        running.getFurthestReached().getStage().name(), running.getFurthestReached().getItemIndex())));
            }
            String reason = startReason(completed, running != null, levelAllowed(detail.getCefrLevel(), learnerLevel), previousDone, anotherRunning);
            TopicCatalogPolicy.Availability start = new TopicCatalogPolicy.Availability(reason == null, reason);
            TopicCatalogPolicy.Availability resume = new TopicCatalogPolicy.Availability(running != null,
                    running == null ? "NO_RUNNING_ATTEMPT" : null);
            boolean retryHere = retryActive && retry.getCurrentAttempt() != null && item.getTopicItemId().equals(retry.getCurrentAttempt().getTopicItemId());
            if (retryHere) {
                var activeRetry = retry.getCurrentAttempt();
                item.setRetryAttempt(new TopicCatalogPolicy.AttemptReference(activeRetry.getId(), new TopicCatalogPolicy.Position(
                        activeRetry.getFurthestReached().getStage().name(), activeRetry.getFurthestReached().getItemIndex())));
            }
            boolean retryAllowed = completed && levelAllowed(detail.getCefrLevel(), learnerLevel) && !retryActive;
            TopicCatalogPolicy.Availability review = new TopicCatalogPolicy.Availability(completed, completed ? null : "NOT_COMPLETED");
            TopicCatalogPolicy.Availability retryStart = new TopicCatalogPolicy.Availability(retryAllowed, retryActive ? "ITEM_RETRY_IN_PROGRESS" : !completed ? "NOT_COMPLETED" : retryAllowed ? null : "LEVEL_TOO_LOW");
            TopicCatalogPolicy.Availability retryResume = new TopicCatalogPolicy.Availability(retryHere, retryHere ? null : "NO_RUNNING_ATTEMPT");
            TopicCatalogPolicy.Availability retryRestart = new TopicCatalogPolicy.Availability(false, "RETRY_RESTART_NOT_ALLOWED");
            item.setActions(new TopicCatalogPolicy.ItemActions(start, resume, review, retryStart, retryResume, retryRestart));
            previousDone &= completed;
        }
        boolean topicComplete = detail.getProgress().status().equals("COMPLETED");
        boolean topicRetry = topicComplete && levelAllowed(detail.getCefrLevel(), learnerLevel);
        detail.setRetryAvailability(new TopicCatalogPolicy.Availability(topicRetry,
                !topicComplete ? "NOT_COMPLETED" : topicRetry ? null : "LEVEL_TOO_LOW"));
        detail.setResetAvailability(new TopicCatalogPolicy.Availability(topicRetry,
                !topicComplete ? "NOT_COMPLETED" : topicRetry ? null : "LEVEL_TOO_LOW"));
        detail.setRetrySession(retryActive ? retrySessionMapper.toResponse(retry) : null);
        return detail;
    }

    private List<TopicSummaryResponse> readAll(GetAvailableTopicRequest request) {
        List<TopicSummaryResponse> rows = new ArrayList<>();
        String cursor = null;
        do {
            TopicPageResponse page = browse.getAvailableTopics(GetAvailableTopicRequest.builder()
                    .level(request.getLevel()).keyword(request.getKeyword()).sort(request.getSort()).limit(100).cursor(cursor).build());
            rows.addAll(page.getItems());
            cursor = page.isHasMore() ? page.getNextCursor() : null;
        } while (cursor != null);
        return rows;
    }

    @Override
    public TopicDiscoveryPageDto discover(String learnerId, TopicDiscoveryQueryDto query) {
        Instant asOf = Instant.now();
        if (query == null) throw new TopicCompanionInvalidQueryException("Query is required");
        requireCompanionPage(learnerId, query.limit(), query.cursor());
        String keyword = query.keyword() == null ? null : query.keyword().trim();
        if (keyword != null && (keyword.isEmpty() || keyword.length() > 80))
            throw new TopicCompanionInvalidQueryException("Invalid keyword");
        CefrLevel learnerLevel = TopicUserLevelResolver.require(userLevels, learnerId);
        CefrLevel filter = query.level() == null ? learnerLevel : query.level();
        String fingerprint = companionFingerprint(learnerId, "discover", filter, keyword, learnerLevel);
        var cursor = companionCursor(query.cursor(), fingerprint);
        var rows = companionCatalog(filter, keyword);
        State state = companionState(learnerId, rows);
        var eligible = rows.stream().filter(row -> levelAllowed(row.topic().getCefrLevel(), learnerLevel)
                && !state.owned().containsKey(row.topic().getId())
                && progress(row.topic().getId(), row.visible(), state).status().equals("NOT_STARTED")).toList();
        var page = companionPage(eligible, cursor, query.limit(), fingerprint, row -> row.topic().getOrderIndex());
        var result = page.rows().stream().map(row -> {
            Topic topic = row.topic();
            return new TopicDiscoveryPageDto.RowDto(topic.getId(), clip(topic.getTitleEn(), 160),
                    clip(topic.getTitleVi(), 160), topic.getCefrLevel(), clip(topic.getDescription(), 240),
                    row.visible().size(), nextCompanionItem(row, state, learnerLevel));
        }).toList();
        return new TopicDiscoveryPageDto(result, page.nextCursor() != null, page.nextCursor(), "COMPLETE", asOf);
    }

    @Override
    public TopicResumePageDto resume(String learnerId, TopicPageQueryDto query) {
        Instant asOf = Instant.now();
        if (query == null) throw new TopicCompanionInvalidQueryException("Query is required");
        requireCompanionPage(learnerId, query.limit(), query.cursor());
        CefrLevel learnerLevel = TopicUserLevelResolver.require(userLevels, learnerId);
        String fingerprint = companionFingerprint(learnerId, "resume", null, null, learnerLevel);
        var cursor = companionCursor(query.cursor(), fingerprint);
        var rows = companionCatalog(null, null);
        State state = companionState(learnerId, rows);
        var eligible = rows.stream().filter(row -> progress(row.topic().getId(), row.visible(), state).status().equals("IN_PROGRESS"))
                .sorted(Comparator.comparing((CompanionRelease row) -> companionAccess(row, state),
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(row -> row.topic().getId())).toList();
        var page = companionPage(eligible, cursor, query.limit(), fingerprint, row -> null);
        var result = page.rows().stream().map(row -> {
            Topic topic = row.topic();
            var progress = progress(topic.getId(), row.visible(), state);
            var active = row.items().stream().filter(item -> {
                var running = state.running().get(item.getTopicItemId());
                return running != null && topic.getId().equals(running.getTopicId())
                        && !completedCompanionItem(item, state);
            }).findFirst().map(item -> new TopicResumePageDto.ActiveItemDto(item.getTopicItemId(),
                    clip(item.getTitle(), 160), state.running().get(item.getTopicItemId()).getId())).orElse(null);
            return new TopicResumePageDto.RowDto(topic.getId(), clip(topic.getTitleEn(), 160),
                    clip(topic.getTitleVi(), 160), topic.getCefrLevel(), progress.status(), progress.completedItemCount(),
                    progress.totalItemCount(), companionAccess(row, state), active, nextCompanionItem(row, state, learnerLevel));
        }).toList();
        return new TopicResumePageDto(result, page.nextCursor() != null, page.nextCursor(), "COMPLETE", asOf);
    }

    @Override
    public TopicCompletionCountsDto counts(String learnerId) {
        Instant asOf = Instant.now();
        requireCompanionPage(learnerId, 1, null);
        var rows = companionCatalog(null, null);
        State state = companionState(learnerId, rows);
        long completedTopics = 0, completedItems = 0;
        for (var row : rows) {
            var progress = progress(row.topic().getId(), row.visible(), state);
            if (progress.status().equals("COMPLETED")) completedTopics++;
            completedItems += progress.completedItemCount();
        }
        return new TopicCompletionCountsDto(asOf, "COMPLETE", completedTopics, completedItems,
                "CURRENT_VISIBLE_CURRENT_GENERATION");
    }

    private List<CompanionRelease> companionCatalog(CefrLevel level, String keyword) {
        List<Topic> candidates = new ArrayList<>();
        Integer key = null;
        String id = null;
        while (true) {
            var batch = topicRepository.findPublishedPage(level, keyword, TopicRepository.SortKey.ORDER_INDEX, key, id, 100);
            candidates.addAll(batch);
            if (batch.size() < 100) break;
            var last = batch.getLast();
            if (Objects.equals(id, last.getId())) throw new IllegalStateException("Catalog cursor stalled");
            id = last.getId();
            key = last.getOrderIndex();
        }
        var releases = historyReleases(candidates.stream().map(Topic::getId).toList());
        Set<String> versionIds = new HashSet<>();
        releases.values().forEach(row -> {
            if (row.published() && row.version().getItemVersionIds() != null)
                versionIds.addAll(row.version().getItemVersionIds());
        });
        Map<String, TopicItemVersion> metadata = new HashMap<>();
        if (!versionIds.isEmpty()) itemVersions.findScreenMetadata(versionIds).forEach(item -> metadata.put(item.getId(), item));
        List<CompanionRelease> result = new ArrayList<>();
        Set<String> seenItems = new HashSet<>();
        for (Topic topic : candidates) {
            var release = releases.get(topic.getId());
            if (release == null || !release.published() || Boolean.TRUE.equals(release.topic().getPublicationSuspended())
                    || !Objects.equals(topic.getLatestPublishedVersion(), release.version().getId())) continue;
            var visible = new LinkedHashMap<String, String>();
            List<TopicItemVersion> ordered = new ArrayList<>();
            if (release.version().getItemVersionIds() != null) {
                for (String versionId : release.version().getItemVersionIds()) {
                    var item = metadata.get(versionId);
                    if (item == null || !topic.getId().equals(item.getTopicId()) || item.getTopicItemId() == null)
                        throw new IllegalStateException("Inconsistent topic manifest");
                    if (item.getStatus() == ContentStatus.HIDDEN) continue;
                    if (item.getStatus() != ContentStatus.PUBLISHED)
                        throw new IllegalStateException("Inconsistent topic item publication");
                    if (!seenItems.add(item.getTopicItemId())) throw new IllegalStateException("Duplicate topic item");
                    visible.put(item.getTopicItemId(), versionId);
                    ordered.add(item);
                }
            }
            ordered.sort(Comparator.comparing(TopicItemVersion::getOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TopicItemVersion::getTopicItemId).thenComparing(TopicItemVersion::getId));
            result.add(new CompanionRelease(topic, visible, ordered));
        }
        return result;
    }

    private State companionState(String learnerId, List<CompanionRelease> rows) {
        if (rows.isEmpty()) return new State(Map.of(), Map.of(), Map.of(), Set.of(), Map.of(), Map.of(), Map.of());
        State state = state(learnerId, rows.stream().map(row -> row.topic().getId()).toList(), true);
        Map<String, TopicItemVersion> pinned = new HashMap<>();
        var pinnedIds = state.running().values().stream().map(UserTopicItemAttempt::getTopicItemVersionId)
                .filter(Objects::nonNull).distinct().toList();
        if (!pinnedIds.isEmpty()) itemVersions.findScreenMetadata(pinnedIds).forEach(item -> pinned.put(item.getId(), item));
        for (var running : state.running().values()) {
            var version = pinned.get(running.getTopicItemVersionId());
            if (version == null || !Objects.equals(version.getTopicId(), running.getTopicId())
                    || !Objects.equals(version.getTopicItemId(), running.getTopicItemId())
                    || (version.getStatus() != ContentStatus.PUBLISHED && version.getStatus() != ContentStatus.ARCHIVED))
                throw new IllegalStateException("Unverified pinned learning version");
        }
        for (var row : rows) {
            for (String itemId : row.visible().keySet()) {
                var item = state.items().get(itemId);
                var running = state.running().get(itemId);
                if (item != null && !row.topic().getId().equals(item.getTopicId())
                        || running != null && !row.topic().getId().equals(running.getTopicId()))
                    throw new IllegalStateException("Inconsistent topic item ownership");
            }
        }
        return state;
    }

    private Instant companionAccess(CompanionRelease row, State state) {
        String topicId = row.topic().getId();
        var owned = state.owned().get(topicId);
        Instant access = owned == null ? null : owned.getLastAccessedAt();
        Instant attempt = state.recency().get(topicId);
        return attempt != null && (access == null || attempt.isAfter(access)) ? attempt : access;
    }

    private boolean completedCompanionItem(TopicItemVersion item, State state) {
        var stored = state.items().get(item.getTopicItemId());
        return stored != null && stored.getStatus() == ItemProgressStatus.COMPLETED;
    }

    private TopicDiscoveryPageDto.ItemDto nextCompanionItem(CompanionRelease row, State state, CefrLevel learnerLevel) {
        boolean anotherRunning = row.visible().keySet().stream().anyMatch(state.running()::containsKey);
        boolean previousDone = true;
        for (var item : row.items()) {
            var stored = state.items().get(item.getTopicItemId());
            boolean completed = completedCompanionItem(item, state);
            boolean running = state.running().containsKey(item.getTopicItemId());
            if ((stored == null || stored.getStatus() == ItemProgressStatus.NOT_STARTED)
                    && startReason(completed, running, levelAllowed(row.topic().getCefrLevel(), learnerLevel),
                    previousDone, anotherRunning) == null)
                return new TopicDiscoveryPageDto.ItemDto(item.getTopicItemId(), clip(item.getTitle(), 160), true);
            previousDone &= completed;
        }
        return null;
    }

    private static String startReason(boolean completed, boolean running, boolean allowed,
                                      boolean previousDone, boolean anotherRunning) {
        return completed ? "ALREADY_COMPLETED" : running ? "OTHER_ITEM_IN_PROGRESS" :
                !allowed ? "LEVEL_TOO_LOW" : !previousDone ? "PREDECESSOR_INCOMPLETE" :
                anotherRunning ? "OTHER_ITEM_IN_PROGRESS" : null;
    }

    private static void requireCompanionPage(String learnerId, int limit, String cursor) {
        if (learnerId == null || learnerId.isBlank() || limit < 1 || limit > 5
                || cursor != null && (cursor.isBlank() || cursor.length() > 512))
            throw new TopicCompanionInvalidQueryException("Invalid learner page query");
    }

    private static String companionFingerprint(String learnerId, String mode, CefrLevel filter,
                                                String keyword, CefrLevel learnerLevel) {
        String owner = HexFormat.of().formatHex(learnerId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return TopicCursorCodec.fingerprint(filter, "companion|" + mode + "|" + owner + "|" + learnerLevel
                + "|" + (keyword == null ? "" : keyword));
    }

    private static TopicCursorCodec.DecodedCursor companionCursor(String cursor, String fingerprint) {
        if (cursor == null) return null;
        TopicCursorCodec.DecodedCursor decoded;
        try {
            decoded = TopicCursorCodec.decode(cursor);
        } catch (InvalidBrowseRequestException ex) {
            throw new TopicCompanionInvalidQueryException("Invalid companion cursor");
        }
        if (decoded.sort() != TopicSortOrder.RECOMMENDED || !fingerprint.equals(decoded.filterFingerprint()))
            throw new TopicCompanionInvalidQueryException("Invalid companion cursor");
        return decoded;
    }

    private CompanionPage companionPage(List<CompanionRelease> rows, TopicCursorCodec.DecodedCursor cursor,
                                         int limit, String fingerprint, java.util.function.Function<CompanionRelease, Integer> key) {
        int start = 0;
        if (cursor != null) {
            while (start < rows.size() && !(rows.get(start).topic().getId().equals(cursor.topicId())
                    && Objects.equals(key.apply(rows.get(start)), cursor.sortKey()))) start++;
            if (start == rows.size()) throw new TopicCompanionInvalidQueryException("Invalid companion cursor");
            start++;
        }
        var page = rows.subList(start, Math.min(start + limit, rows.size()));
        String next = null;
        if (start + page.size() < rows.size()) {
            var last = page.getLast();
            next = TopicCursorCodec.encode(TopicSortOrder.RECOMMENDED, fingerprint, key.apply(last), last.topic().getId());
            if (next.length() > 512) throw new IllegalStateException("Companion cursor exceeds limit");
        }
        return new CompanionPage(page, next);
    }

    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) return value;
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }

    private record CompanionRelease(Topic topic, Map<String, String> visible, List<TopicItemVersion> items) {}
    private record CompanionPage(List<CompanionRelease> rows, String nextCursor) {}

    @Override
    public Map<String, TopicSpeakingHistoryDto> summaries(String learnerId, Collection<String> topicIds) {
        Map<String, HistoryRelease> releases = historyReleases(topicIds);
        if (releases.isEmpty()) return Map.of();

        Map<String, TopicItemVersion> versionById = new HashMap<>();
        Set<String> itemIds = new HashSet<>();
        for (HistoryRelease row : releases.values()) {
            if (row.version().getItemVersionIds() != null) itemIds.addAll(row.version().getItemVersionIds());
        }
        itemVersions.findAllById(itemIds).forEach(row -> versionById.put(row.getId(), row));

        State state = state(learnerId, releases.keySet());
        Map<String, TopicSpeakingHistoryDto> result = new HashMap<>();
        releases.forEach((id, row) -> {
            Map<String, String> visible = new HashMap<>();
            if (row.version().getItemVersionIds() != null) {
                for (String versionId : row.version().getItemVersionIds()) {
                    TopicItemVersion item = versionById.get(versionId);
                    if (item != null && id.equals(item.getTopicId()) && item.getStatus() == ContentStatus.PUBLISHED) {
                        visible.put(item.getTopicItemId(), versionId);
                    }
                }
            }
            var progress = progress(id, visible, state);
            LearningStatus status = LearningStatus.valueOf(progress.status());
            if (row.published()) {
                result.put(id, new TopicSpeakingHistoryDto(status, title(row.topic()), row.topic().getCefrLevel(),
                        new TopicSpeakingHistoryDto.Progress(progress.completedItemCount(),
                                progress.totalItemCount(), progress.percent())));
            } else if (status != LearningStatus.NOT_STARTED) {
                result.put(id, new TopicSpeakingHistoryDto(status, null, null, null));
            }
        });
        return Map.copyOf(result);
    }

    @Override
    public String publishedTitle(String topicId) {
        HistoryRelease row = historyReleases(Set.of(topicId)).get(topicId);
        return row != null && row.published() ? title(row.topic()) : null;
    }

    @Override
    public Map<String, String> publishedCovers(Collection<String> topicIds) {
        Map<String, String> result = new HashMap<>();
        historyReleases(topicIds).forEach((id, row) -> {
            if (!row.published()) return;
            String cover = media.resolveCover(row.topic());
            if (cover != null) result.put(id, cover);
        });
        return Map.copyOf(result);
    }

    private Map<String, HistoryRelease> historyReleases(Collection<String> topicIds) {
        if (topicIds.isEmpty()) return Map.of();
        List<Topic> rows = topicRepository.findAllById(topicIds);
        List<String> versionIds = rows.stream().map(Topic::getLatestPublishedVersion)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, TopicVersion> byVersion = new HashMap<>();
        versions.findAllById(versionIds).forEach(row -> byVersion.put(row.getId(), row));
        Map<String, HistoryRelease> result = new HashMap<>();
        for (Topic row : rows) {
            TopicVersion release = byVersion.get(row.getLatestPublishedVersion());
            if (release != null && row.getId().equals(release.getTopicId())
                    && (release.getStatus() == ContentStatus.PUBLISHED || release.getStatus() == ContentStatus.HIDDEN)) {
                result.put(row.getId(), new HistoryRelease(row, release));
            }
        }
        return result;
    }

    private static String title(Topic topic) {
        String titleVi = topic.getTitleVi();
        if (titleVi != null && !titleVi.isBlank()) return titleVi;
        String titleEn = topic.getTitleEn();
        return titleEn != null && !titleEn.isBlank() ? titleEn : null;
    }

    private State state(String userId) {
        return state(userId, null);
    }

    private State state(String userId, Collection<String> topicIds) {
        return state(userId, topicIds, false);
    }

    private State state(String userId, Collection<String> topicIds, boolean companion) {
        boolean scoped = topicIds != null;
        Map<String, Long> observed = scoped ? generations.snapshot(userId, topicIds) : generations.snapshot(userId);
        Map<String, UserTopicProgress> owned = new HashMap<>();
        var topicProgress = scoped ? topics.findByUserIdAndTopicIdIn(userId, topicIds) : topics.findByUserId(userId);
        topicProgress.stream().filter(row -> !companion || companionOwned(userId, topicIds, row.getUserId(), row.getTopicId())).filter(row -> row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L))
                .forEach(row -> owned.put(row.getTopicId(), row));
        Map<String, UserTopicItemProgress> storedProgress = new HashMap<>();
        var itemProgress = scoped ? items.findByUserIdAndTopicIdIn(userId, topicIds) : items.findByUserId(userId);
        itemProgress.stream().filter(row -> !companion || companionOwned(userId, topicIds, row.getUserId(), row.getTopicId())).filter(row -> row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L))
                .forEach(row -> storedProgress.put(row.getTopicItemId(), row));
        var itemAttempts = scoped ? attempts.findByUserIdAndTopicIdIn(userId, topicIds) : attempts.findByUserId(userId);
        List<UserTopicItemAttempt> saved = new ArrayList<>(itemAttempts.stream()
                .filter(row -> !companion || companionOwned(userId, topicIds, row.getUserId(), row.getTopicId()))
                .filter(row -> row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L)).toList());
        Map<String, TopicRetryRuntime> retryStates = new HashMap<>();
        var runtimes = scoped ? retryRuntimes.findByUserIdAndTopicIdIn(userId, topicIds) : retryRuntimes.findByUserId(userId);
        for (TopicRetryRuntime runtime : runtimes) {
            if (companion && !companionOwned(userId, topicIds, runtime.getUserId(), runtime.getTopicId())) continue;
            if (runtime.getGeneration() != observed.getOrDefault(runtime.getTopicId(), 0L)) continue;
            retryStates.put(runtime.getTopicId(), runtime);
            if (!companion) {
                saved.addAll(runtime.getOfficialResults().values());
                if (runtime.getCurrentAttempt() != null) saved.add(runtime.getCurrentAttempt());
            } else {
                if (runtime.getOfficialResults() != null) runtime.getOfficialResults().forEach((itemId, attempt) -> {
                    if (validCompanionRetry(runtime, attempt) && Objects.equals(itemId, attempt.getTopicItemId())
                            && attempt.getStatus() == AttemptStatus.COMPLETED) saved.add(attempt);
                });
                var current = runtime.getCurrentAttempt();
                if (current != null && validCompanionRetry(runtime, current)) saved.add(current);
            }
        }
        Map<String, UserTopicItemProgress> progress = resolveCompletedProgress(storedProgress, saved);
        List<UserTopicItemAttempt> active = saved.stream().filter(row -> row.getStatus() == AttemptStatus.IN_PROGRESS).toList();
        Set<String> learning = new HashSet<>();
        List<String> parentAttemptIds = active.stream().map(UserTopicItemAttempt::getUserTopicAttemptId)
                .filter(Objects::nonNull).distinct().toList();
        var parentAttempts = scoped ? sessions.findByUserIdAndTopicIdInAndIdIn(userId, topicIds, parentAttemptIds)
                : sessions.findAllById(parentAttemptIds);
        parentAttempts.forEach(row -> {
            if (row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L)
                    && row.getAttemptType() == AttemptType.LEARNING) learning.add(row.getId());
        });
        Map<String, UserTopicItemAttempt> running = new HashMap<>();
        if (!companion) {
            active.stream().filter(row -> row.getAttemptType() == AttemptType.LEARNING || learning.contains(row.getUserTopicAttemptId())).forEach(row -> running.put(row.getTopicItemId(), row));
        } else {
            Map<String, UserTopicAttempt> parents = new HashMap<>();
            parentAttempts.forEach(parent -> parents.put(parent.getId(), parent));
            for (var row : active) {
                var parent = parents.get(row.getUserTopicAttemptId());
                if (row.getAttemptType() == AttemptType.RETRY) continue;
                // The current LEARN endpoint persists a standalone item attempt; legacy attempts use a parent.
                if (row.getUserTopicAttemptId() == null && row.getAttemptType() == AttemptType.LEARNING
                        && row.getStartRequestKey() != null && !row.getStartRequestKey().isBlank()
                        && Objects.equals(row.getStartFingerprint(), "start|" + row.getTopicItemId() + "|LEARN")
                        && row.getStartedAt() != null) {
                    if (running.put(row.getTopicItemId(), row) != null)
                        throw new IllegalStateException("Multiple learning attempts");
                    continue;
                }
                if (parent == null || !userId.equals(parent.getUserId()) || !row.getTopicId().equals(parent.getTopicId())
                        || parent.getGeneration() != row.getGeneration() || parent.getAttemptType() != AttemptType.LEARNING
                        || parent.getStatus() != AttemptStatus.IN_PROGRESS
                        || !Objects.equals(parent.getCurrentTopicItemId(), row.getTopicItemId())
                        || !(parent.getScope() == AttemptScope.ITEM && Objects.equals(parent.getTargetTopicItemId(), row.getTopicItemId())
                        || parent.getItemsSnapshot() != null && parent.getItemsSnapshot().stream().anyMatch(item ->
                        Objects.equals(item.getTopicItemId(), row.getTopicItemId())
                                && Objects.equals(item.getTopicItemVersionId(), row.getTopicItemVersionId()))))
                    throw new IllegalStateException("Unverified learning attempt parent");
                if (running.put(row.getTopicItemId(), row) != null)
                    throw new IllegalStateException("Multiple learning attempts");
            }
        }
        Set<String> started = new HashSet<>();
        Map<String, Instant> recency = new HashMap<>();
        for (UserTopicItemAttempt attempt : saved) {
            started.add(attempt.getTopicId());
            if (attempt.getLastAccessedAt() != null) recency.merge(attempt.getTopicId(), attempt.getLastAccessedAt(),
                    (left, right) -> left.isAfter(right) ? left : right);
        }
        return new State(owned, progress, running, started, recency, retryStates, observed);
    }

    private static boolean companionOwned(String owner, Collection<String> topicIds, String rowOwner, String topicId) {
        if (!owner.equals(rowOwner) || !topicIds.contains(topicId))
            throw new IllegalStateException("Inconsistent learner state");
        return true;
    }

    private static boolean validCompanionRetry(TopicRetryRuntime runtime, UserTopicItemAttempt attempt) {
        return attempt != null && Objects.equals(runtime.getUserId(), attempt.getUserId())
                && Objects.equals(runtime.getTopicId(), attempt.getTopicId())
                && runtime.getGeneration() == attempt.getGeneration()
                && attempt.getAttemptType() == AttemptType.RETRY;
    }

    private Map<String, UserTopicItemProgress> resolveCompletedProgress(
            Map<String, UserTopicItemProgress> storedProgress, List<UserTopicItemAttempt> attempts) {
        Map<String, UserTopicItemProgress> resolved = new HashMap<>(storedProgress);
        attempts.stream()
                .filter(attempt -> attempt.getStatus() == AttemptStatus.COMPLETED
                        && attempt.getCompletionResult() != null)
                .sorted(Comparator.comparing(UserTopicItemAttempt::getCompletedAt)
                        .thenComparing(UserTopicItemAttempt::getId))
                .forEach(attempt -> {
                    UserTopicItemProgress previous = resolved.get(attempt.getTopicItemId());
                    if (previous != null && previous.getCompletedAt() != null
                            && previous.getCompletedAt().isAfter(attempt.getCompletedAt())) {
                        return;
                    }
                    resolved.put(attempt.getTopicItemId(), UserTopicItemProgress.builder()
                            .generation(attempt.getGeneration())
                            .userId(attempt.getUserId())
                            .topicId(attempt.getTopicId())
                            .topicItemId(attempt.getTopicItemId())
                            .status(ItemProgressStatus.COMPLETED)
                            .lastCompletedAttemptId(attempt.getId())
                            .completedTopicItemVersionId(attempt.getTopicItemVersionId())
                            .completedAt(attempt.getCompletedAt())
                            .lastAccessedAt(attempt.getLastAccessedAt())
                            .firstTryCorrectCount(attempt.getCompletionResult().getFirstTryCorrectCount())
                            .totalQuizCount(attempt.getCompletionResult().getTotalQuizCount())
                            .build());
                });
        return resolved;
    }

    private void enrich(TopicSummaryResponse row, State state, CefrLevel learnerLevel) {
        row.setProgress(progress(row.getTopicId(), row.getVisibleItemVersions(), state));
        row.setHasNewContent(hasNew(row.getVisibleItemVersions(), state));
        UserTopicProgress owned = state.owned().get(row.getTopicId());
        Instant accessed = owned == null ? null : owned.getLastAccessedAt();
        Instant attemptAccessed = state.recency().get(row.getTopicId());
        if (attemptAccessed != null && (accessed == null || attemptAccessed.isAfter(accessed))) accessed = attemptAccessed;
        for (UserTopicItemAttempt attempt : state.running().values()) {
            if (row.getTopicId().equals(attempt.getTopicId()) && attempt.getLastAccessedAt() != null &&
                    (accessed == null || attempt.getLastAccessedAt().isAfter(accessed))) accessed = attempt.getLastAccessedAt();
        }
        row.setLastAccessedAt(accessed);
        boolean allowed = levelAllowed(row.getCefrLevel(), learnerLevel);
        row.setStartAvailability(new TopicCatalogPolicy.Availability(allowed, allowed ? null : "LEVEL_TOO_LOW"));
    }

    private TopicCatalogPolicy.ProgressSummary progress(String topicId, Map<String, String> visible, State state) {
        int complete = 0;
        boolean started = state.owned().containsKey(topicId) || state.started().contains(topicId);
        for (String id : visible.keySet()) {
            UserTopicItemProgress p = state.items().get(id);
            if (p != null && p.getStatus() == ItemProgressStatus.COMPLETED) complete++;
            if (p != null && p.getStatus() != ItemProgressStatus.NOT_STARTED || state.running().containsKey(id)) started = true;
        }
        String status = !visible.isEmpty() && complete == visible.size() ? "COMPLETED" : started ? "IN_PROGRESS" : "NOT_STARTED";
        return new TopicCatalogPolicy.ProgressSummary(status, complete, visible.size(), visible.isEmpty() ? 0 : (int) Math.round(100.0 * complete / visible.size()));
    }

    private boolean hasNew(Map<String, String> visible, State state) {
        return visible.entrySet().stream().anyMatch(entry -> {
            UserTopicItemProgress p = state.items().get(entry.getKey());
            return p != null && p.getStatus() == ItemProgressStatus.COMPLETED && !Objects.equals(p.getCompletedTopicItemVersionId(), entry.getValue());
        });
    }

    private boolean levelAllowed(CefrLevel required, CefrLevel learnerLevel) {
        return required == null || required.ordinal() <= learnerLevel.ordinal();
    }
    private int limit(Integer value) {
        int limit = value == null ? 20 : value;
        if (limit < 1 || limit > 100) throw new InvalidBrowseRequestException("limit must be between 1 and 100");
        return limit;
    }
    private TopicSortOrder sort(GetAvailableTopicRequest request) {
        return TopicSortOrder.fromApiValue(request.getSort()).orElseThrow(() -> new InvalidBrowseRequestException("Invalid sort"));
    }
    private String fingerprint(GetAvailableTopicRequest request, String userId) {
        CefrLevel level = request.getLevel() == null || request.getLevel().isBlank() ? null : CefrLevel.valueOf(request.getLevel().trim().toUpperCase(Locale.ROOT));
        String keyword = request.getKeyword() == null ? "" : request.getKeyword().trim();
        return TopicCursorCodec.fingerprint(level, keyword + "|" + userId + "|discover");
    }
    private boolean after(TopicSummaryResponse row, TopicCursorCodec.DecodedCursor cursor, TopicSortOrder sort) {
        Integer key = sort == TopicSortOrder.DURATION ? row.getDurationMinutes() : row.getSortOrderIndex();
        int comparison = Comparator.nullsFirst(Integer::compareTo).compare(key, cursor.sortKey());
        return comparison > 0 || comparison == 0 && row.getTopicId().compareTo(cursor.topicId()) > 0;
    }
    private String cursor(TopicSummaryResponse row, GetAvailableTopicRequest request, String fingerprint) {
        TopicSortOrder sort = sort(request);
        return TopicCursorCodec.encode(sort, fingerprint, sort == TopicSortOrder.DURATION ? row.getDurationMinutes() : row.getSortOrderIndex(), row.getTopicId());
    }
    private record State(Map<String, UserTopicProgress> owned, Map<String, UserTopicItemProgress> items,
                         Map<String, UserTopicItemAttempt> running, Set<String> started, Map<String, Instant> recency,
                         Map<String, TopicRetryRuntime> retries, Map<String, Long> generations) {}

    private record HistoryRelease(Topic topic, TopicVersion version) {
        private boolean published() {
            return version.getStatus() == ContentStatus.PUBLISHED;
        }
    }
}
