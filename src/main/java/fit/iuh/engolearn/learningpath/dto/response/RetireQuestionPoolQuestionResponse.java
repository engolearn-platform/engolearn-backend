package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import java.time.Instant;
import java.util.List;

public record RetireQuestionPoolQuestionResponse(
        String questionId, QuestionPoolContentStatus status, int revision, String retiredBy, Instant retiredAt,
        QuestionPoolCoverageResponse coverage, List<QuestionPoolCoverageWarningResponse> warnings) {
    public RetireQuestionPoolQuestionResponse { warnings = List.copyOf(warnings); }
}
