package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.request.LearningPathListStatus;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.LearningPathListResponse;

public interface LearningPathService {
    LearningPathListResponse getByUserId(String userId, LearningPathListStatus status);
    LearningPathListResponse search(String userId, String keyword);
    LearningPathDetailResponse getDetail(String pathId, String userId);
}
