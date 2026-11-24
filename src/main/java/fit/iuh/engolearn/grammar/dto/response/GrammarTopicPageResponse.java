package fit.iuh.engolearn.grammar.dto.response;

import java.util.List;

public record GrammarTopicPageResponse(List<GrammarManagementTopicResponse> items, long total, GrammarStatsResponse stats) {
}
