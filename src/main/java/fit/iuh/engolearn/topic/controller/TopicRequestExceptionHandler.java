package fit.iuh.engolearn.topic.controller;

import fit.iuh.engolearn.topic.exception.TopicRequestException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = { TopicAttemptController.class, TopicRetryController.class, TopicLearningController.class, TopicBrowseController.class })
public class TopicRequestExceptionHandler {
    @ExceptionHandler(TopicRequestException.class)
    public ProblemDetail handle(TopicRequestException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(error.getStatus()), error.getMessage());
        problem.setTitle(error.getCode());
        problem.setProperty("code", error.getCode());
        return problem;
    }
}
