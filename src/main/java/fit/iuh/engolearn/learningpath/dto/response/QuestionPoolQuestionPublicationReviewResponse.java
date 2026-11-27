package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionReviewCheck;
import java.time.Instant;
import java.util.List;

public record QuestionPoolQuestionPublicationReviewResponse(
        Integer sourceRevision, String passageId, Integer passageRevision,
        List<QuestionReviewCheck> confirmedChecks, String reviewedBy, Instant reviewedAt) {
    public QuestionPoolQuestionPublicationReviewResponse {
        confirmedChecks = confirmedChecks == null ? null : List.copyOf(confirmedChecks);
    }
}
