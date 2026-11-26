package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementResultResponse;
import fit.iuh.engolearn.placement.dto.response.PlacementReviewResponse;

public interface PlacementResultService {
    PlacementResultResponse getResult(String attemptId, String currentUserId);
    PlacementReviewResponse getReview(String attemptId, String currentUserId);
}
