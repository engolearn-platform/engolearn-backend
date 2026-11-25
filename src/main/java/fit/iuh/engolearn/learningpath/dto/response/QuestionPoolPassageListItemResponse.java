package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import java.time.Instant;

public record QuestionPoolPassageListItemResponse(
        String passageId, String title, QuestionPoolSkill skill, CefrLevel level,
        QuestionPoolContentStatus status, int revision, boolean hasAudio,
        Instant createdAt, Instant updatedAt) {
}
