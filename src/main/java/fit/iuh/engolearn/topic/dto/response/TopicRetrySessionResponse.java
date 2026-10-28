package fit.iuh.engolearn.topic.dto.response;

import java.time.Instant;
import java.util.List;

public record TopicRetrySessionResponse(String retrySessionId, String topicId, String scope, String status,
        List<String> itemIds, String currentAttemptId, Instant startedAt, Instant completedAt,
        List<String> completedItemIds, List<Snapshot> itemsSnapshot, String nextTopicItemId) {
    public record Snapshot(String topicItemId, String topicItemVersionId, int orderIndex) {}
}
