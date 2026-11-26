package fit.iuh.engolearn.speaking.service.impl;

import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.speaking.dto.response.SpeakingHistoryResponse.*;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import fit.iuh.engolearn.speaking.mapper.SpeakingSessionMapper;
import fit.iuh.engolearn.speaking.model.SpeakingSession;
import fit.iuh.engolearn.speaking.repository.SpeakingHistoryQueries;
import fit.iuh.engolearn.speaking.repository.SpeakingHistoryQueries.ParentGroup;
import fit.iuh.engolearn.speaking.service.SpeakingHistory;
import fit.iuh.engolearn.topic.contract.TopicSpeakingHistory;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingHistoryDto;
import fit.iuh.engolearn.topic.contract.dto.TopicSpeakingHistoryDto.LearningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingHistoryImpl implements SpeakingHistory {
    private static final int METADATA_BATCH_SIZE = 100;
    private static final TopicSpeakingHistoryDto UNKNOWN = new TopicSpeakingHistoryDto(
            LearningStatus.UNKNOWN, null, null, null);

    private final CurrentUserProvider users;
    private final SpeakingHistoryQueries queries;
    private final TopicSpeakingHistory topicHistory;

    @Override
    @PreAuthorize("hasRole('USER')")
    public Groups topics(Group group, int page, int size) {
        validatePage(page, size);
        if (group == null) throw invalid("A Speaking history group is required");
        String owner = users.requireCurrentUserId();
        long offset = (long) page * size;
        Map<Group, Long> totals = new EnumMap<>(Group.class);
        List<SelectedTopic> selected = new ArrayList<>(size);
        try (var stream = queries.streamGroups(owner, METADATA_BATCH_SIZE)) {
            var groups = stream.iterator();
            while (groups.hasNext()) {
                List<ParentGroup> batch = new ArrayList<>(METADATA_BATCH_SIZE);
                while (batch.size() < METADATA_BATCH_SIZE && groups.hasNext()) batch.add(groups.next());
                var metadata = topicHistory.summaries(owner, batch.stream().map(ParentGroup::topicId).toList());
                for (ParentGroup row : batch) {
                    var current = metadata.getOrDefault(row.topicId(), UNKNOWN);
                    Group classified = classify(current.learningStatus());
                    long count = totals.merge(classified, 1L, Long::sum);
                    if (group == classified && count > offset && selected.size() < size) {
                        selected.add(new SelectedTopic(row, current));
                    }
                }
            }
            Map<String, String> covers = selected.isEmpty() ? Map.of() : topicHistory.publishedCovers(selected.stream()
                    .filter(row -> row.current().learningProgress() != null).map(row -> row.saved().topicId()).toList());
            List<Topic> items = selected.stream().map(row -> row.summary(covers.get(row.saved().topicId()))).toList();
            return new Groups(items, page, size, totals.getOrDefault(group, 0L) > offset + size,
                    new Totals(totals.getOrDefault(Group.IN_PROGRESS, 0L),
                            totals.getOrDefault(Group.COMPLETED, 0L), totals.getOrDefault(Group.OTHER, 0L)));
        } catch (DataAccessException | com.mongodb.MongoException failure) {
            log.warn("Unable to read Speaking history groups", failure);
            throw new SpeakingException(HttpStatus.SERVICE_UNAVAILABLE, "HISTORY_UNAVAILABLE",
                    "Speaking history groups are temporarily unavailable", true);
        }
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    public ParentSessions parent(String topicId, int page, int size) {
        validatePage(page, size);
        if (!StringUtils.hasText(topicId) || !topicId.equals(topicId.strip())) throw invalid("Invalid Topic id");
        String owner = users.requireCurrentUserId();
        long count = queries.parentCount(owner, topicId);
        if (count == 0) return new ParentSessions(List.of(), page, size, false, topicId, null, 0);

        List<SpeakingSession> rows = (long) page * size >= count ? List.of()
                : queries.parentRows(owner, topicId, page, size);
        List<Session> items = rows.stream().limit(size).map(SpeakingHistoryImpl::session).toList();
        String title = null;
        try {
            title = topicHistory.publishedTitle(topicId);
        } catch (DataAccessException failure) {
            log.warn("Unable to read current Topic title for Speaking history", failure);
        }
        if (!StringUtils.hasText(title)) title = queries.savedParentTitle(owner, topicId);
        return new ParentSessions(items, page, size, rows.size() > size, topicId, title, count);
    }

    private static Session session(SpeakingSession row) {
        var saved = row.getSnapshot();
        return new Session(row.getId(), SpeakingSessionMapper.scope(saved),
                new Context(saved.getTitle(), saved.getTopicTitle(), saved.getTopicItemTitle()), row.getCreatedAt(),
                row.getCommittedTurnCount(), row.getSpeechDurationMs(), row.getStatus(), row.getAssessmentStatus());
    }

    private static Group classify(LearningStatus status) {
        return switch (status) {
            case IN_PROGRESS -> Group.IN_PROGRESS;
            case COMPLETED -> Group.COMPLETED;
            case NOT_STARTED, UNKNOWN -> Group.OTHER;
        };
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("Page must be non-negative and size between 1 and 100");
        }
    }

    private static SpeakingException invalid(String message) {
        return new SpeakingException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, false);
    }

    private record SelectedTopic(ParentGroup saved, TopicSpeakingHistoryDto current) {
        private Topic summary(String cover) {
            String title = StringUtils.hasText(current.title()) ? current.title() : saved.savedTitle();
            var level = current.level() == null ? saved.savedLevel() : current.level();
            var progress = current.learningProgress();
            return new Topic(saved.topicId(), title, level, cover, current.learningStatus(),
                    progress == null ? null : new Progress(progress.completedItemCount(), progress.totalItemCount(),
                            progress.percent()), saved.sessionCount(), saved.latestCreatedAt());
        }
    }
}
