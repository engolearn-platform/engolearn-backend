package fit.iuh.engolearn.placement.dto.response;

import java.util.List;

public record PlacementAdminAttemptPageResponse(
        List<PlacementAdminAttemptSummaryResponse> items,
        long total,
        int page,
        int size
) {
}
