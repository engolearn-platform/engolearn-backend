package fit.iuh.engolearn.learning.progress.dto;

import fit.iuh.engolearn.content.api.TopicDetailView;
import fit.iuh.engolearn.content.api.TopicItemDetailView;
import java.time.Instant;
import java.util.List;

public record TopicDetailWithProgressResponse(
        TopicDetailView topic, TopicProgressSummary progress, List<ItemProgressView> items) {

    public record TopicProgressSummary(
            Double progressPercent,
            Boolean isCompleted,
            String continueItemId,
            Boolean isOutdated,
            Instant lastAccessedAt) {
    }

    public record ItemProgressView(
            TopicItemDetailView item,
            String status,
            String furthestStage,
            String furthestStep,
            Double speakingScore,
            Instant completedAt) {
    }
}
