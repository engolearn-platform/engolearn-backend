package fit.iuh.engolearn.topic.contract.dto;

import java.time.Instant;

public record TopicCompletionCountsDto(Instant asOf, String coverage, long completedTopics,
                                       long completedTopicItems, String scope) {}
