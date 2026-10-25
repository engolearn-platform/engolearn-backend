package fit.iuh.engolearn.learningpath.exception;

import fit.iuh.engolearn.learningpath.dto.response.LearningPathRecommendationResponse;

public class RecommendationChangedException extends RuntimeException {

    private final LearningPathRecommendationResponse recommendation;

    public RecommendationChangedException(LearningPathRecommendationResponse recommendation) {
        super("Đề xuất đã thay đổi, vui lòng xem và xác nhận lại");
        this.recommendation = recommendation;
    }

    public LearningPathRecommendationResponse getRecommendation() {
        return recommendation;
    }
}
