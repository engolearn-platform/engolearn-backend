
package fit.iuh.engolearn.learningpath.dto.response;

import java.util.List;

public record LearningPathListItemResponse(
        String id,
        String sourceAssessmentAttemptId,
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
