package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.request.CreatePlacementTestSetRequest;
import fit.iuh.engolearn.placement.dto.request.UpdatePlacementTestSetRequest;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetPageResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementAdminTestSetResponse;
import fit.iuh.engolearn.placement.model.enums.PlacementTestSetStatus;

public interface PlacementAdminTestSetService {
    PlacementAdminTestSetPageResponse list(String search, PlacementTestSetStatus status, int page, int size);
    PlacementAdminTestSetResponse get(String id);
    PlacementAdminTestSetResponse create(CreatePlacementTestSetRequest request);
    PlacementAdminTestSetResponse update(String id, UpdatePlacementTestSetRequest request);
    PlacementAdminTestSetResponse publish(String id, UpdatePlacementTestSetRequest request);
    PlacementAdminTestSetResponse newVersion(String id);
    PlacementAdminTestSetResponse archive(String id, long revision);
    void delete(String id, long revision);
}
