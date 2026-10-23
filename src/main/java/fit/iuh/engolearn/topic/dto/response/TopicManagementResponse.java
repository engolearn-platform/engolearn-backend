package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;

import java.time.Instant;

/**
 * E1/E2/E3 response body (design record F05 §4.5). {@code status} is the
 * derived lowercase wire value {@code "draft"|"published"}; {@code durationMinutes}
 * and {@code itemCount} are computed on read and never persisted.
 */
public record TopicManagementResponse(
        String id,
        String titleEn,
        String titleVi,
        CefrLevel cefr,
        TopicCategory category,
        String description,
        String coverUrl,
        String status,
        int durationMinutes,
        int itemCount,
        Instant createdAt,
        Instant updatedAt) {
}
