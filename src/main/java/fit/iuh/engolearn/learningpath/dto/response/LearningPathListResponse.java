package fit.iuh.engolearn.learningpath.dto.response;

import fit.iuh.engolearn.microskill.contract.dto.MicroSkillDto;
import java.util.List;

public record LearningPathListResponse(
        List<LearningPathListItemResponse> items,
        SectionCounts counts,
        List<MicroSkillDto> microSkillCatalog
) {
    public LearningPathListResponse { microSkillCatalog = List.copyOf(microSkillCatalog); }

    public record SectionCounts(
            long active,
            long completed,
            long cancelled
    ) {}
}
