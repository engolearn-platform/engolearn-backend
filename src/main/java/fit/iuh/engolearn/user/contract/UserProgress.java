package fit.iuh.engolearn.user.contract;

import fit.iuh.engolearn.user.contract.dto.UserProgressSummaryDto;

import java.util.Optional;

/**
 * Cross-feature capability of the user feature: a user's progress on a topic.
 * Designed as if the provider were remote — {@link Optional} for "not found",
 * no persistence types.
 */
public interface UserProgress {

    Optional<UserProgressSummaryDto> findProgress(String userId, String topicId);
}
