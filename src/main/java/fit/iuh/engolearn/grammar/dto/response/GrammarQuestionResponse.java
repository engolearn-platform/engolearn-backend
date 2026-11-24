package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarQuestionType;
import java.util.List;

public record GrammarQuestionResponse(String id, int number, GrammarQuestionType type, String prompt,
                                   List<GrammarOptionResponse> options) {
}
