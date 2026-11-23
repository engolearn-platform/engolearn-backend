package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import java.util.List;

public record GrammarLevelResponse(GrammarLevel level, boolean unlocked, long completedTopics, long totalTopics,
                                int percent, String lockHint, List<GrammarTopicResponse> topics) {
}
