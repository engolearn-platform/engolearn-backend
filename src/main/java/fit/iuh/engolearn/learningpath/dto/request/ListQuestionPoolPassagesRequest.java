package fit.iuh.engolearn.learningpath.dto.request;

import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolContentStatus;
import fit.iuh.engolearn.learningpath.model.enums.QuestionPoolSkill;
import fit.iuh.engolearn.shared.enums.CefrLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ListQuestionPoolPassagesRequest(
        @Size(max = 200) String keyword,
        QuestionPoolSkill skill,
        CefrLevel level,
        QuestionPoolContentStatus status,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {
    public ListQuestionPoolPassagesRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
