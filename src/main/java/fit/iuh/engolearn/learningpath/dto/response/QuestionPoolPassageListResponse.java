package fit.iuh.engolearn.learningpath.dto.response;

import java.util.List;

public record QuestionPoolPassageListResponse(
        List<QuestionPoolPassageListItemResponse> items, int page, int size,
        long totalElements, int totalPages) {
    public QuestionPoolPassageListResponse {
        items = List.copyOf(items);
    }
}
