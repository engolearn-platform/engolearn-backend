package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import java.time.Instant;

public record QuestionPoolQuestionRetirementResponse(
        String questionId, QuestionPoolContentStatus status, int revision, String retiredBy, Instant retiredAt) {
}
