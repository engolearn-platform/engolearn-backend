
package fit.iuh.engolearn.learningpath.dto;

import java.util.List;

public record LearningPathListItemResponse(
        String id,
        String sourceAttemptId,
        String createdDate,
        String level,
        Integer k,
        List<String> microSkills,
        String status,
        Integer currentWeekNo,
        ProgressSummary progress
) {
    public record ProgressSummary(
            Integer assigned,
            Integer answered
    ) {}
}
