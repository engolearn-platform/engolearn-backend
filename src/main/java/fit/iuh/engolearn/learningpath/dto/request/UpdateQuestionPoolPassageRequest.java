package fit.iuh.engolearn.learningpath.dto.request;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateQuestionPoolPassageRequest(
        @NotNull @Min(1) Integer revision,
        QuestionPoolSkill skill,
        CefrLevel level,
        @Size(max = 200) String title,
        @Size(max = 50000) String text,
        @Size(max = 50000) String transcript) {
}
