package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import java.time.Instant;

public record QuestionPoolQuestionMutationResponse(
        String questionId, QuestionPoolContentStatus status, Integer revision,
        String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {
}
