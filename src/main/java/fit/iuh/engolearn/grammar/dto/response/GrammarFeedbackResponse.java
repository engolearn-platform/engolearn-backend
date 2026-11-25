package fit.iuh.engolearn.grammar.dto.response;

import fit.iuh.engolearn.grammar.model.enums.GrammarQuestionType;

public record GrammarFeedbackResponse(String questionId, int number, boolean correct, String submittedAnswer,
                                   String correctAnswer, String explanation, GrammarQuestionType type, String prompt) {
}
