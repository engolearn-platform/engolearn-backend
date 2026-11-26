package fit.iuh.engolearn.grammar.dto.response;

import java.util.List;

public record GrammarContentBlockResponse(String id,
                        String type,
                        String title, String text, String formula,
                        List<GrammarContentExampleResponse> examples,
                        List<String> ruleItems,
                        String incorrect, String correct, String explanation) {
}
