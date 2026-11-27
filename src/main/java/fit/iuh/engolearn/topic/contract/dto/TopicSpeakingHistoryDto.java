package fit.iuh.engolearn.topic.contract.dto;

import fit.iuh.engolearn.shared.enums.CefrLevel;

public record TopicSpeakingHistoryDto(LearningStatus learningStatus, String title, CefrLevel level,
                                      Progress learningProgress) {
    public enum LearningStatus { IN_PROGRESS, COMPLETED, NOT_STARTED, UNKNOWN }

    public record Progress(int completedItemCount, int totalItemCount, int percent) {}
}
