package fit.iuh.engolearn.placement.service;

import fit.iuh.engolearn.placement.dto.response.PlacementLearningResponse;

public interface PlacementLearningService {
    PlacementLearningResponse activate(String attemptId, String userId);
}
