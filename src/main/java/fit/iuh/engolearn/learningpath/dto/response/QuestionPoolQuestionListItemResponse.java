package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.time.Instant;

public record QuestionPoolQuestionListItemResponse(
        String questionId, QuestionPoolSkill skill, String microSkill, CefrLevel level,
        String passageId, String prompt, QuestionPoolContentStatus status, Integer revision,
        Instant createdAt, Instant updatedAt) {
}
