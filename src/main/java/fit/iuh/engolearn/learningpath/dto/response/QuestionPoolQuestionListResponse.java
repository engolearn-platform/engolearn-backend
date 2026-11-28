package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import java.util.List;

public record QuestionPoolQuestionListResponse(
        List<QuestionPoolQuestionListItemResponse> items, int page, int size,
        long totalElements, int totalPages,
        List<MicroSkillDto> microSkillCatalog
) {
    public QuestionPoolQuestionListResponse {
        items = List.copyOf(items);
        microSkillCatalog = List.copyOf(microSkillCatalog);
    }
}
