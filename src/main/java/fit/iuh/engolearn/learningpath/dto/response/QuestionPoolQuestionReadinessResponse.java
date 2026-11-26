package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import java.util.List;

public record QuestionPoolQuestionReadinessResponse(
        String questionId, Integer revision, String status, boolean structurallyValid,
        String passageId, Integer passageRevision,
        List<QuestionPoolQuestionReadinessIssueResponse> blockers,
        List<QuestionPoolQuestionReadinessIssueResponse> warnings,
        List<QuestionReviewCheck> requiredReviewChecks) {
    public QuestionPoolQuestionReadinessResponse {
        blockers = List.copyOf(blockers);
        warnings = List.copyOf(warnings);
        requiredReviewChecks = List.copyOf(requiredReviewChecks);
    }
}
