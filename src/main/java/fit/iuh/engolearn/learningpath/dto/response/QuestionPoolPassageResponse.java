package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.time.Instant;

public record QuestionPoolPassageResponse(
        String passageId, QuestionPoolSkill skill, CefrLevel level, String title,
        String text, String transcript, String audioMediaId,
        QuestionPoolContentStatus status, int revision, String createdBy,
        Instant createdAt, Instant updatedAt) {
}
