package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse;

public interface LearningPathRecommendationService {
    LearningPathRecommendationResponse recommend(String userId, String assessmentAttemptId);
    LearningPathRecommendationResponse recommendForAssessment(String userId, String assessmentAttemptId);
}
