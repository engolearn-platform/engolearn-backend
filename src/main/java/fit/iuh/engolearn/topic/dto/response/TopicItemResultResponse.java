package fit.iuh.engolearn.topic.dto.response;

import java.time.Instant;

public record TopicItemResultResponse(String attemptId, String topicId, String topicItemId, String title,
        int firstTryCorrectCount, int totalQuizCount, int accuracyPercent, Instant completedAt,
        String nextTopicItemId, TopicCatalogPolicy.Availability nextItemAvailability,
        TopicCatalogPolicy.RetrySession retrySession) {}
