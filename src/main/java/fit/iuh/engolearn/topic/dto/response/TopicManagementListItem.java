package fit.iuh.engolearn.topic.dto.response;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;

import java.time.Instant;

/**
 * E4 list entry — the seven {@code BR-F5.8} fields plus {@code id}
 * (design record F05 §4.6).
 */
public record TopicManagementListItem(
        String id,
        String titleEn,
        String titleVi,
        CefrLevel cefr,
        TopicCategory category,
        String status,
        int itemCount,
        Instant updatedAt) {
}
