package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import java.util.List;

public record PlacementAdminAttemptQuestionResponse(
        String id,
        String code,
        PlacementSkill skill,
        PlacementLevel level,
        String prompt,
        String passage,
        List<PlacementOptionResponse> options,
        String correctOptionId,
        String explanation,
        String transcript,
        String audioUrl,
        PlacementQuestionStatus status
) {
}
