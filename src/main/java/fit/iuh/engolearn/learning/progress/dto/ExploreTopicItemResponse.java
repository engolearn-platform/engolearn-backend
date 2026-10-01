package fit.iuh.engolearn.learning.progress.dto;

import fit.iuh.engolearn.content.api.TopicCardView;
import java.time.Instant;

public record ExploreTopicItemResponse(TopicCardView topic, TopicProgressView progress) {

    public record TopicProgressView(
            Double progressPercent,
            Boolean isCompleted,
            String continueItemId,
            Boolean isOutdated,
            Integer userVersion,
            Instant lastAccessedAt) {
    }
}
