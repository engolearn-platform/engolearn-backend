package fit.iuh.engolearn.topic.contract;

import fit.iuh.engolearn.topic.contract.dto.TopicSummaryDto;

import java.util.Optional;

/**
 * Cross-feature capability of the topic feature: a stable summary of a topic.
 * Designed as if the provider were remote — {@link Optional} for "not found",
 * no persistence types.
 */
public interface TopicSummary {

    Optional<TopicSummaryDto> findSummary(String topicId);
}
