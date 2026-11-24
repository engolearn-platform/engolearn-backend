package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarBlockType;
import java.util.List;

public record GrammarBlockResponse(String id, GrammarBlockType type, String title, String text, String formula,
                                List<GrammarExampleResponse> examples, List<String> ruleItems, String incorrect,
                                String correct, String explanation) {
}
