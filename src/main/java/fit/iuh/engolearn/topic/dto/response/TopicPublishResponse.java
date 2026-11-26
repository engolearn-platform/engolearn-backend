package fit.iuh.engolearn.topic.dto.response;

import java.time.Instant;
import java.util.List;

public record TopicPublishResponse(String topicId, long revision, String topicVersionId, int version,
                                   List<PublishedItem> items, long itemCount, long durationMinutes,
                                   Instant publishedAt, String previousTopicVersionId, List<ChangedItem> changes) {
    public TopicPublishResponse(String topicId, long revision, String topicVersionId, int version,
                                List<PublishedItem> items, long itemCount, long durationMinutes, Instant publishedAt) {
        this(topicId, revision, topicVersionId, version, items, itemCount, durationMinutes, publishedAt, null, List.of());
    }

    public record PublishedItem(String topicItemId, String itemVersionId, int version, int order,
                                String title, int durationMinutes) {
    }

    public record ChangedItem(String topicItemId, String previousItemVersionId, String itemVersionId,
                              int version, String changeType) {
    }
}
