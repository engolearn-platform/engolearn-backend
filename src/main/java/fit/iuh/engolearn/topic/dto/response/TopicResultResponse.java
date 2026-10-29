package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

public record TopicResultResponse(String topicId, String titleVi, int firstTryCorrectCount, int totalQuizCount,
        int accuracyPercent, List<Item> items, TopicCatalogPolicy.Availability retryAvailability, TopicCatalogPolicy.Availability resetAvailability) {
    public record Item(String topicItemId, String title, String completedAttemptId, int firstTryCorrectCount,
            int totalQuizCount, int accuracyPercent, boolean hasNewContent, TopicCatalogPolicy.ItemActions actions) {}
}
