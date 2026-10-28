package fit.iuh.engolearn.topic.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuizAnswerResponse(String quizId, @JsonProperty("isCorrect") boolean isCorrect,
                                 QuizFeedbackResponse feedback, TopicAttemptResponse attempt) {}
