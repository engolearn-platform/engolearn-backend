package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.dto.request.GetAvailableTopicRequest;
import fit.iuh.engolearn.topic.dto.request.GetTopicDetailRequest;
import fit.iuh.engolearn.topic.dto.response.*;
import fit.iuh.engolearn.topic.exception.InvalidBrowseRequestException;
import fit.iuh.engolearn.topic.model.*;
import fit.iuh.engolearn.topic.model.enums.*;
import fit.iuh.engolearn.topic.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class TopicLearnerCatalogService {
    private final TopicBrowseService browse;
    private final LocalLearnerProvider learner;
    private final UserTopicProgressRepository topics;
    private final UserTopicItemProgressRepository items;
    private final UserTopicItemAttemptRepository attempts;
    private final UserTopicAttemptRepository sessions;

    public TopicLearnerCatalogService(TopicBrowseService browse, LocalLearnerProvider learner,
            UserTopicProgressRepository topics, UserTopicItemProgressRepository items,
            UserTopicItemAttemptRepository attempts, UserTopicAttemptRepository sessions) {
        this.browse = browse; this.learner = learner; this.topics = topics;
        this.items = items; this.attempts = attempts; this.sessions = sessions;
    }

    public TopicPageResponse discover(GetAvailableTopicRequest request) {
        String userId = learner.getCurrentUserId();
        if (userId == null) return browse.getAvailableTopics(request);
        State state = state(userId);
        List<TopicSummaryResponse> rows = readAll(request);
        rows.forEach(row -> enrich(row, state));
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

    public MyTopicPageResponse mine(String status, Integer requestedLimit, String cursor) {
        String userId = learner.getCurrentUserId();
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Select a local learner profile");
        if (!Set.of("IN_PROGRESS", "COMPLETED").contains(status)) throw new InvalidBrowseRequestException("Invalid topic status");
        State state = state(userId);
        List<TopicSummaryResponse> owned = readAll(GetAvailableTopicRequest.builder().build());
        owned.forEach(row -> enrich(row, state));
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

    public TopicDetailResponse detail(String topicId) {
        TopicDetailResponse detail = browse.getTopicDetail(GetTopicDetailRequest.builder().topicId(topicId).build());
        String userId = learner.getCurrentUserId();
        if (userId == null) return detail;
        State state = state(userId);
        Map<String, String> visible = new LinkedHashMap<>();
        detail.getItems().forEach(item -> visible.put(item.getTopicItemId(), item.getPublishedVersionId()));
        detail.setProgress(progress(topicId, visible, state));
        detail.setHasNewContent(hasNew(visible, state));
        boolean previousDone = true;
        boolean anotherRunning = state.running().values().stream().anyMatch(a -> topicId.equals(a.getTopicId()));
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
            String reason = completed ? "ALREADY_COMPLETED" : running != null ? "OTHER_ITEM_IN_PROGRESS" :
                    !levelAllowed(detail.getCefrLevel()) ? "LEVEL_TOO_LOW" : !previousDone ? "PREDECESSOR_INCOMPLETE" :
                    anotherRunning ? "OTHER_ITEM_IN_PROGRESS" : null;
            TopicCatalogPolicy.Availability start = new TopicCatalogPolicy.Availability(reason == null, reason);
            TopicCatalogPolicy.Availability resume = new TopicCatalogPolicy.Availability(running != null,
                    running == null ? "NO_RUNNING_ATTEMPT" : null);
            TopicCatalogPolicy.Availability unavailable = new TopicCatalogPolicy.Availability(false, "CONTENT_UNAVAILABLE");
            item.setActions(new TopicCatalogPolicy.ItemActions(start, resume, unavailable, unavailable, unavailable, unavailable));
            previousDone &= completed;
        }
        detail.setRetryAvailability(new TopicCatalogPolicy.Availability(false,
                detail.getProgress().status().equals("COMPLETED") ? "CONTENT_UNAVAILABLE" : "NOT_COMPLETED"));
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

    private State state(String userId) {
        Map<String, UserTopicProgress> owned = new HashMap<>();
        topics.findByUserId(userId).forEach(row -> owned.put(row.getTopicId(), row));
        Map<String, UserTopicItemProgress> progress = new HashMap<>();
        items.findByUserId(userId).forEach(row -> progress.put(row.getTopicItemId(), row));
        List<UserTopicItemAttempt> active = attempts.findByUserId(userId).stream().filter(row -> row.getStatus() == AttemptStatus.IN_PROGRESS).toList();
        Set<String> learning = new HashSet<>();
        sessions.findAllById(active.stream().map(UserTopicItemAttempt::getUserTopicAttemptId).filter(Objects::nonNull).distinct().toList())
                .forEach(row -> { if (row.getAttemptType() == AttemptType.LEARNING) learning.add(row.getId()); });
        Map<String, UserTopicItemAttempt> running = new HashMap<>();
        active.stream().filter(row -> row.getAttemptType() == AttemptType.LEARNING || learning.contains(row.getUserTopicAttemptId())).forEach(row -> running.put(row.getTopicItemId(), row));
        return new State(owned, progress, running);
    }

    private void enrich(TopicSummaryResponse row, State state) {
        row.setProgress(progress(row.getTopicId(), row.getVisibleItemVersions(), state));
        row.setHasNewContent(hasNew(row.getVisibleItemVersions(), state));
        UserTopicProgress owned = state.owned().get(row.getTopicId());
        Instant accessed = owned == null ? null : owned.getLastAccessedAt();
        for (UserTopicItemAttempt attempt : state.running().values()) {
            if (row.getTopicId().equals(attempt.getTopicId()) && attempt.getLastAccessedAt() != null &&
                    (accessed == null || attempt.getLastAccessedAt().isAfter(accessed))) accessed = attempt.getLastAccessedAt();
        }
        row.setLastAccessedAt(accessed);
        boolean allowed = levelAllowed(row.getCefrLevel());
        row.setStartAvailability(new TopicCatalogPolicy.Availability(allowed, allowed ? null : "LEVEL_TOO_LOW"));
    }

    private TopicCatalogPolicy.ProgressSummary progress(String topicId, Map<String, String> visible, State state) {
        int complete = 0;
        boolean started = state.owned().containsKey(topicId) || state.running().values().stream().anyMatch(a -> topicId.equals(a.getTopicId()));
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

    private boolean levelAllowed(CefrLevel required) { return required.ordinal() <= learner.getCurrentLevel().ordinal(); }
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
                         Map<String, UserTopicItemAttempt> running) {}
}
