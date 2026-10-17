package fit.iuh.engolearn.topic.contract.dto;

/**
 * Stable contract view of a topic. Part of the topic contract: meant for other
 * features, never shaped for the frontend.
 */
public record TopicSummaryDto(String topicId, String title) {
}
