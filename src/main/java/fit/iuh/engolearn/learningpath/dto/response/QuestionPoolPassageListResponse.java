package fit.iuh.engolearn.learningpath.dto.response;

import java.util.List;

public record QuestionPoolPassageListResponse(
        List<QuestionPoolPassageListItemResponse> items, int page, int size,
        long totalElements, int totalPages, QuestionPoolPassageCountersResponse counters) {
    public QuestionPoolPassageListResponse {
        items = List.copyOf(items);
    }
}
