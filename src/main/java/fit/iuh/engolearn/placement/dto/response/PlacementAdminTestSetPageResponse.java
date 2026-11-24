package fit.iuh.engolearn.placement.dto.response;

import java.util.List;

public record PlacementAdminTestSetPageResponse(List<PlacementAdminTestSetResponse> items, long total, int page, int size) {}
