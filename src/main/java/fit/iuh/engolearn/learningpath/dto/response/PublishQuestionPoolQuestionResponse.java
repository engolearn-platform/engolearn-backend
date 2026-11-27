package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import java.time.Instant;
import java.util.List;

public record PublishQuestionPoolQuestionResponse(
        String questionId, QuestionPoolContentStatus status, int revision, int reviewedRevision,
        String passageId, Integer reviewedPassageRevision, String publishedBy, Instant publishedAt,
        List<QuestionReviewCheck> confirmedReviewChecks, List<QuestionPoolQuestionReadinessIssueResponse> warnings) {
    public PublishQuestionPoolQuestionResponse {
        confirmedReviewChecks = List.copyOf(confirmedReviewChecks);
        warnings = List.copyOf(warnings);
    }
}
