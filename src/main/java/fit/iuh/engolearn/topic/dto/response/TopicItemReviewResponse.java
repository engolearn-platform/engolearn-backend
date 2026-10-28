package fit.iuh.engolearn.topic.dto.response;

import java.util.List;

public record TopicItemReviewResponse(TopicAttemptResponse attempt, TopicItemContentResponse content, List<ReviewedQuiz> quizzes) {
    public record ReviewedQuiz(QuizResponse quiz, QuizFeedbackResponse feedback, boolean firstTryCorrect) {}
}
