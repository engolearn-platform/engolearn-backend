package fit.iuh.engolearn.learningpath.dto.request;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateQuestionPoolQuestionRequest(
        @NotNull @Min(1) Integer revision,
        QuestionPoolSkill skill,
        @Size(max = 200) String microSkill,
        CefrLevel level,
        @Size(max = 100) String passageId,
        @Size(max = 50000) String prompt,
        @Size(min = 4, max = 4) List<@NotNull @Valid QuestionPoolQuestionOptionRequest> options,
        @Pattern(regexp = "[A-D]") String correctOptionId,
        @Size(max = 50000) String explanation) {
}
