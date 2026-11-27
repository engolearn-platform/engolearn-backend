package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import java.time.Instant;

public record RetireQuestionPoolPassageResponse(String passageId, QuestionPoolContentStatus status,
        int revision, String retiredBy, Instant retiredAt) {
}
