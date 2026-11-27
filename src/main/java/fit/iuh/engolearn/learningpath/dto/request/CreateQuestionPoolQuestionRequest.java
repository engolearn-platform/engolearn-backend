package fit.iuh.engolearn.learningpath.dto.request;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateQuestionPoolQuestionRequest(
        @NotNull QuestionPoolSkill skill,
        @NotBlank @Size(max = 200) String microSkill,
        @NotNull CefrLevel level,
        @Size(max = 100) String passageId,
        @NotBlank @Size(max = 50000) String prompt,
        @NotNull @Size(min = 4, max = 4) List<@NotNull @Valid QuestionPoolQuestionOptionRequest> options,
        @NotBlank @Pattern(regexp = "[A-D]") String correctOptionId,
        @NotBlank @Size(max = 50000) String explanation) {
}
