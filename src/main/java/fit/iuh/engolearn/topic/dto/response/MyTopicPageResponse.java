package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

public record MyTopicPageResponse(List<TopicSummaryResponse> items, long total, Counts counts, String nextCursor, boolean hasMore) {
    public record Counts(long inProgress, long completed) {}
}
