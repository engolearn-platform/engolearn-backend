package fit.iuh.engolearn.content.api;

import java.util.List;

public record QuizModuleView(
        String quizId,
        String quizType,
        String title,
        String instructions,
        Integer orderIndex,
        List<QuizQuestionView> questions) {
}
