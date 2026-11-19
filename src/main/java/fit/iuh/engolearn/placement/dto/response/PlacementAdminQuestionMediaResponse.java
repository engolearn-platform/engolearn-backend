package fit.iuh.engolearn.placement.dto.response;

import java.util.List;

public record PlacementAdminQuestionMediaResponse(List<ItemResponse> items) {
    public record ItemResponse(String audioUrl, double durationSeconds, String title) {}
}
