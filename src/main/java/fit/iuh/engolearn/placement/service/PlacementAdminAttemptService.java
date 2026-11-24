package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.request.PlacementAdminAttemptSearchRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptDetailResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminAttemptPageResponse;

public interface PlacementAdminAttemptService {
    PlacementAdminAttemptPageResponse search(PlacementAdminAttemptSearchRequest request);
    PlacementAdminAttemptDetailResponse getDetail(String id);
}
