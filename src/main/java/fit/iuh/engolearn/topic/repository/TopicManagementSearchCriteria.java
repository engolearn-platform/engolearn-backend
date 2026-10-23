package fit.iuh.engolearn.topic.repository;

import fit.iuh.engolearn.shared.enums.CefrLevel;
import fit.iuh.engolearn.topic.model.enums.TopicCategory;

/**
 * Immutable filter carrier for the management topic list (design record F05 §4.6).
 *
 * <p>All filters are optional and combine with AND. {@code published} is the
 * parsed form of the wire {@code status} param: {@code true} → published
 * ({@code latestPublishedVersion != null}), {@code false} → draft
 * ({@code latestPublishedVersion == null}), {@code null} → no status filter.</p>
 */
public record TopicManagementSearchCriteria(
        String search,
        CefrLevel cefr,
        TopicCategory category,
        Boolean published) {
}
