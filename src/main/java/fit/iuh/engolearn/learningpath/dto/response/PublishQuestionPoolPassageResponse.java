package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.PassageReviewCheck;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import java.time.Instant;
import java.util.List;

public record PublishQuestionPoolPassageResponse(
        String passageId, QuestionPoolContentStatus status, int revision, int reviewedRevision,
        String publishedBy, Instant publishedAt, List<PassageReviewCheck> confirmedReviewChecks,
        List<QuestionPoolPassageReadinessIssueResponse> warnings) {
    public PublishQuestionPoolPassageResponse {
        confirmedReviewChecks = List.copyOf(confirmedReviewChecks);
        warnings = List.copyOf(warnings);
    }
}
