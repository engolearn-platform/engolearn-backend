package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.AdvanceTopicContentRequest;
import fit.iuh.engolearn.topic.dto.request.AnswerQuizRequest;
import fit.iuh.engolearn.topic.dto.request.CompleteTopicAttemptRequest;
import fit.iuh.engolearn.topic.dto.request.StartTopicAttemptRequest;
import fit.iuh.engolearn.topic.dto.response.CurrentQuizResponse;
import fit.iuh.engolearn.topic.dto.response.QuizAnswerResponse;
import fit.iuh.engolearn.topic.dto.response.QuizNavigationResponse;
import fit.iuh.engolearn.topic.dto.response.TopicAttemptResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemContentResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemResultResponse;
import fit.iuh.engolearn.topic.dto.response.TopicItemReviewResponse;
import fit.iuh.engolearn.topic.dto.response.TopicProgressResponse;
import fit.iuh.engolearn.topic.dto.response.TopicResultResponse;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import java.time.Instant;
import java.util.Map;

public interface TopicAttemptService {
    TopicAttemptResponse start(String userId, String itemId, StartTopicAttemptRequest body, String key);

    TopicAttemptResponse get(String userId, String id);

    TopicItemContentResponse content(String userId, String id);

    CurrentQuizResponse getCurrentQuiz(String userId, String id);

    QuizNavigationResponse quizNavigation(String userId, String id);

    TopicAttemptResponse advance(String userId, String id, AdvanceTopicContentRequest body, String key);

    QuizAnswerResponse answerQuiz(String userId, String id, String quizId, AnswerQuizRequest body, String key);

    TopicAttemptResponse complete(String userId, String id, CompleteTopicAttemptRequest body, String key);

    TopicItemResultResponse itemResult(String userId, String id);

    TopicItemReviewResponse review(String userId, String id);

    TopicResultResponse topicResult(String userId, String topicId);

    Map<String, Instant> access(String userId, String id, String key);

    TopicAttemptResponse describe(UserTopicItemAttempt attempt);

    TopicProgressResponse topicProgress(String userId, String topicId);
}
