package fit.iuh.engolearn.topic.dto.response;

import java.util.List;
import fit.iuh.engolearn.topic.model.enums.QuizType;

public record QuizNavigationResponse(TopicAttemptResponse attempt, List<Entry> entries) {
    public record Entry(String quizId, QuizType type, int index, String status, QuizResponse quiz,
            QuizFeedbackResponse feedback, Boolean firstTryCorrect) {}
}
