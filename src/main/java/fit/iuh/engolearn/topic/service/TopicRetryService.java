package fit.iuh.engolearn.topic.service;

import fit.iuh.engolearn.topic.dto.request.AdvanceTopicContentRequest;
import fit.iuh.engolearn.topic.dto.request.AnswerQuizRequest;
import fit.iuh.engolearn.topic.dto.request.StartTopicAttemptRequest;
import fit.iuh.engolearn.topic.dto.request.StartTopicRetryRequest;
import fit.iuh.engolearn.topic.dto.response.QuizAnswerResponse;
import fit.iuh.engolearn.topic.dto.response.TopicAttemptResponse;
import fit.iuh.engolearn.topic.dto.response.TopicRetrySessionResponse;
import fit.iuh.engolearn.topic.model.TopicRetryRuntime;
import fit.iuh.engolearn.topic.model.UserTopicItemAttempt;
import java.time.Instant;
import java.util.Map;

public interface TopicRetryService {
    boolean isRetry(String id);

    UserTopicItemAttempt owned(String id, boolean result);

    TopicAttemptResponse startItem(String itemId,StartTopicAttemptRequest body,String key);

    TopicRetrySessionResponse startTopic(String topicId,StartTopicRetryRequest body,String key);

    TopicRetrySessionResponse getSession(String id);

    TopicAttemptResponse advance(String id,AdvanceTopicContentRequest body,String key);

    QuizAnswerResponse answer(String id,String quizId,AnswerQuizRequest body,String key);

    Map<String,Instant> access(String id,String key);

    TopicRetryRuntime runtime(String topicId);

    TopicRetryRuntime runtime(String topicId,long generation);
}
