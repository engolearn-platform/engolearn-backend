package fit.iuh.engolearn.topic.service.impl;

import fit.iuh.engolearn.topic.infra.dev.LocalLearnerProvider;
import fit.iuh.engolearn.topic.service.*;
import fit.iuh.engolearn.topic.utils.TopicCursorCodec;
import lombok.RequiredArgsConstructor;

import fit.iuh.engolearn.shared.enums.CefrLevel;
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
public class TopicLearnerCatalogServiceImpl implements TopicLearnerCatalogService {
    private final TopicBrowseService browse;
    private final LocalLearnerProvider learner;
    private final UserTopicProgressRepository topics;
    private final UserTopicItemProgressRepository items;
    private final UserTopicItemAttemptRepository attempts;
    private final UserTopicAttemptRepository sessions;
    private final TopicGenerationService generations;
    private final TopicRetryRuntimeRepository retryRuntimes;
    private final TopicRetrySessionMapper retrySessionMapper;

    @Override
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

    @Override
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

    @Override
    public TopicDetailResponse detail(String topicId) {
        TopicDetailResponse detail = browse.getTopicDetail(GetTopicDetailRequest.builder().topicId(topicId).build());
        String userId = learner.getCurrentUserId();
        if (userId == null) {
            detail.setResetAvailability(new TopicCatalogPolicy.Availability(false, "SIGN_IN_REQUIRED"));
            return detail;
        }
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
            String reason = completed ? "ALREADY_COMPLETED" : running != null ? "OTHER_ITEM_IN_PROGRESS" :
                    !levelAllowed(detail.getCefrLevel()) ? "LEVEL_TOO_LOW" : !previousDone ? "PREDECESSOR_INCOMPLETE" :
                    anotherRunning ? "OTHER_ITEM_IN_PROGRESS" : null;
            TopicCatalogPolicy.Availability start = new TopicCatalogPolicy.Availability(reason == null, reason);
            TopicCatalogPolicy.Availability resume = new TopicCatalogPolicy.Availability(running != null,
                    running == null ? "NO_RUNNING_ATTEMPT" : null);
            boolean retryHere = retryActive && retry.getCurrentAttempt() != null && item.getTopicItemId().equals(retry.getCurrentAttempt().getTopicItemId());
            if (retryHere) {
                var activeRetry = retry.getCurrentAttempt();
                item.setRetryAttempt(new TopicCatalogPolicy.AttemptReference(activeRetry.getId(), new TopicCatalogPolicy.Position(
                        activeRetry.getFurthestReached().getStage().name(), activeRetry.getFurthestReached().getItemIndex())));
            }
            boolean retryAllowed = completed && levelAllowed(detail.getCefrLevel()) && !retryActive;
            TopicCatalogPolicy.Availability review = new TopicCatalogPolicy.Availability(completed, completed ? null : "NOT_COMPLETED");
            TopicCatalogPolicy.Availability retryStart = new TopicCatalogPolicy.Availability(retryAllowed, retryActive ? "ITEM_RETRY_IN_PROGRESS" : !completed ? "NOT_COMPLETED" : retryAllowed ? null : "LEVEL_TOO_LOW");
            TopicCatalogPolicy.Availability retryResume = new TopicCatalogPolicy.Availability(retryHere, retryHere ? null : "NO_RUNNING_ATTEMPT");
            TopicCatalogPolicy.Availability retryRestart = new TopicCatalogPolicy.Availability(false, "RETRY_RESTART_NOT_ALLOWED");
            item.setActions(new TopicCatalogPolicy.ItemActions(start, resume, review, retryStart, retryResume, retryRestart));
            previousDone &= completed;
        }
        boolean topicComplete = detail.getProgress().status().equals("COMPLETED");
        boolean topicRetry = topicComplete && levelAllowed(detail.getCefrLevel());
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

    private State state(String userId) {
        Map<String, Long> observed = generations.snapshot(userId);
        Map<String, UserTopicProgress> owned = new HashMap<>();
        topics.findByUserId(userId).stream().filter(row -> row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L)).forEach(row -> owned.put(row.getTopicId(), row));
        Map<String, UserTopicItemProgress> storedProgress = new HashMap<>();
        items.findByUserId(userId).stream().filter(row -> row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L)).forEach(row -> storedProgress.put(row.getTopicItemId(), row));
        List<UserTopicItemAttempt> saved = new ArrayList<>(attempts.findByUserId(userId).stream().filter(row -> row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L)).toList());
        Map<String, TopicRetryRuntime> retryStates = new HashMap<>();
        for (TopicRetryRuntime runtime : retryRuntimes.findByUserId(userId)) {
            if (runtime.getGeneration() != observed.getOrDefault(runtime.getTopicId(), 0L)) continue;
            retryStates.put(runtime.getTopicId(), runtime);
            saved.addAll(runtime.getOfficialResults().values());
            if (runtime.getCurrentAttempt() != null) saved.add(runtime.getCurrentAttempt());
        }
        Map<String, UserTopicItemProgress> progress = resolveCompletedProgress(storedProgress, saved);
        List<UserTopicItemAttempt> active = saved.stream().filter(row -> row.getStatus() == AttemptStatus.IN_PROGRESS).toList();
        Set<String> learning = new HashSet<>();
        sessions.findAllById(active.stream().map(UserTopicItemAttempt::getUserTopicAttemptId).filter(Objects::nonNull).distinct().toList())
                .forEach(row -> { if (row.getGeneration() == observed.getOrDefault(row.getTopicId(), 0L) && row.getAttemptType() == AttemptType.LEARNING) learning.add(row.getId()); });
        Map<String, UserTopicItemAttempt> running = new HashMap<>();
        active.stream().filter(row -> row.getAttemptType() == AttemptType.LEARNING || learning.contains(row.getUserTopicAttemptId())).forEach(row -> running.put(row.getTopicItemId(), row));
        Set<String> started = new HashSet<>();
        Map<String, Instant> recency = new HashMap<>();
        for (UserTopicItemAttempt attempt : saved) {
            started.add(attempt.getTopicId());
            if (attempt.getLastAccessedAt() != null) recency.merge(attempt.getTopicId(), attempt.getLastAccessedAt(),
                    (left, right) -> left.isAfter(right) ? left : right);
        }
        return new State(owned, progress, running, started, recency, retryStates, observed);
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

    private void enrich(TopicSummaryResponse row, State state) {
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
        boolean allowed = levelAllowed(row.getCefrLevel());
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
                         Map<String, UserTopicItemAttempt> running, Set<String> started, Map<String, Instant> recency,
                         Map<String, TopicRetryRuntime> retries, Map<String, Long> generations) {}
}
