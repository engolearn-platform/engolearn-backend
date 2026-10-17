package fit.iuh.engolearn.user.contract.dto;

import java.time.Instant;

/**
 * Stable contract view of a user's progress on a topic. Part of the user
 * contract: meant for other features, never shaped for the frontend.
 */
public record UserProgressSummaryDto(
        String lastTopicItemId,
        boolean completed,
        Instant completedAt,
        Instant lastAccessedAt) {
}
