package fit.iuh.engolearn.topic.dto.response;

import java.time.Instant;

public record TopicAttemptResponse(String attemptId, String topicId, String topicItemId, String topicItemVersionId,
        int version, String mode, String status, long revision, TopicCatalogPolicy.Position position,
        Progress progress, String retrySessionId, Instant startedAt, Instant lastAccessedAt, Instant completedAt) {
    public record Progress(int completedStepCount, int totalStepCount, int percent, int learnedVocabularyCount,
                           int learnedExpressionCount, int completedQuizCount, int totalQuizCount) {}
}
