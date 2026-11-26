package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import java.util.List;

public record GrammarResultResponse(int score, int totalQuestions, boolean passed, boolean completed,
                                 boolean topicCompleted, String nextLessonId, boolean archived,
                                 List<GrammarLevel> newlyUnlockedLevels) {
}
