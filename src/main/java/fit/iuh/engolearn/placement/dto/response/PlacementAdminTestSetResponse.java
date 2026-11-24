package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import fit.iuh.engolearn.placement.model.enums.PlacementQuestionStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementSkill;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;
import java.time.Instant;
import java.util.List;

public record PlacementAdminTestSetResponse(
        String id, String code, String name, int version, PlacementTestSetStatus status, List<String> questionIds,
        List<QuestionResponse> snapshot, Instant createdAt, long revision, boolean lockedSeed) {
    public record QuestionResponse(String id, String code, String prompt, PlacementSkill skill, PlacementLevel level,
                                   List<PlacementOptionResponse> options, String correctOptionId, String passage,
                                   String audioUrl, String transcript, String explanation, PlacementQuestionStatus status) {}
}
