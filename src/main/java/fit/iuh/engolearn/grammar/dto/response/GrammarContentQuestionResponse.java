package fit.iuh.engolearn.grammar.dto.response;

import java.util.List;

public record GrammarContentQuestionResponse(String id, String type,
                           String prompt, String explanation, List<String> options,
                           Integer correctIndex, String correctAnswer, List<String> acceptedAnswers) {
}
