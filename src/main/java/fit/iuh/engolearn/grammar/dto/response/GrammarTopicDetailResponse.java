package fit.iuh.engolearn.grammar.dto.response;

import java.util.List;

public record GrammarTopicDetailResponse(GrammarTopicResponse topic, List<GrammarLessonSummaryResponse> lessons) {
}
