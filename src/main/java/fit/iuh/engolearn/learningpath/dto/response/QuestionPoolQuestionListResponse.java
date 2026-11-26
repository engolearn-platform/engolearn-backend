package fit.iuh.engolearn.learningpath.dto.response;

import java.util.List;

public record QuestionPoolQuestionListResponse(
        List<QuestionPoolQuestionListItemResponse> items, int page, int size,
        long totalElements, int totalPages) {
    public QuestionPoolQuestionListResponse {
        items = List.copyOf(items);
    }
}
