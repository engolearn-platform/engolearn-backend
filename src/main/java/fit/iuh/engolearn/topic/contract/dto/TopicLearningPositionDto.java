package fit.iuh.engolearn.topic.contract.dto;

import fit.iuh.engolearn.topic.contract.enums.TopicContextStage;

public record TopicLearningPositionDto(TopicContextStage stage, int itemIndex) {}
