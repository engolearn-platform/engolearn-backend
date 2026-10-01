package fit.iuh.engolearn.content.api;

import java.util.List;
import java.util.Map;

public record QuizQuestionView(
        String prompt,
        String audioUrl,
        List<QuizOptionView> options,
        String correct,
        String explanationOk,
        String explanationNg,
        QuizSentenceView sentence,
        Map<String, Object> quizData,
        Map<String, Object> answerData,
        String explanation,
        String tips) {
}
