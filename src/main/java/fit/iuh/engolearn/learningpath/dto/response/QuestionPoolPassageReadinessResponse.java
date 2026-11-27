package fit.iuh.engolearn.learningpath.dto.response;

import java.util.List;

public record QuestionPoolPassageReadinessResponse(
        String passageId, Integer revision, String status, boolean structurallyValid,
        long referencingQuestionCount, long incompatibleQuestionCount,
        List<QuestionPoolPassageReadinessIssueResponse> blockers,
        List<QuestionPoolPassageReadinessIssueResponse> warnings, List<String> requiredReviewChecks) {
    public QuestionPoolPassageReadinessResponse {
        blockers = List.copyOf(blockers);
        warnings = List.copyOf(warnings);
        requiredReviewChecks = List.copyOf(requiredReviewChecks);
    }
}
