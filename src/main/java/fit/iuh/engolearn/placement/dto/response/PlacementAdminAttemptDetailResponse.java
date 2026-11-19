package fit.iuh.engolearn.placement.dto.response;

import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptSummaryResponse.Kind;
import fit.iuh.engolearn.placement.model.enums.PlacementAttemptStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementClaimStatus;
import fit.iuh.engolearn.placement.model.enums.PlacementLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PlacementAdminAttemptDetailResponse(
        String id,
        String name,
        Kind kind,
        String userId,
        String testSetId,
        String testSetCode,
        int testSetVersion,
        Instant startedAt,
        Instant deadlineAt,
        Instant endedAt,
        PlacementAttemptStatus status,
        Integer correctCount,
        Integer totalQuestions,
        PlacementLevel level,
        PlacementClaimStatus claimStatus,
        List<PlacementAdminAttemptQuestionResponse> snapshot,
        Map<String, String> answers
) {
}
