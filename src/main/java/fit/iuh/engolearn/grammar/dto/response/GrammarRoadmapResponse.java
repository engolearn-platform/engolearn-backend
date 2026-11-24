package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarLevel;
import java.util.List;

public record GrammarRoadmapResponse(GrammarLevel currentLevel, List<GrammarLevel> newlyUnlockedLevels,
                                  List<GrammarLevelResponse> levels) {
}
