package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.topic.controller.TopicLearningController;
import fit.iuh.engolearn.topic.exception.LearningPreconditionFailedException;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(assignableTypes = TopicLearningController.class)
public class TopicLearningExceptionHandler {

    @ExceptionHandler(TopicItemNotFoundException.class)
    public ProblemDetail handleTopicItemNotFound(TopicItemNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Topic item not found", exception.getMessage());
    }

    @ExceptionHandler(TopicNotFoundException.class)
    public ProblemDetail handleTopicNotFound(TopicNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Topic not found", exception.getMessage());
    }

    @ExceptionHandler(LearningPreconditionFailedException.class)
    public ProblemDetail handleLearningPrecondition(LearningPreconditionFailedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Learning precondition failed", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleStaleLearningPosition(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Learning position changed",
                "The learning position was saved by another request; reload it and try again");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid learning request",
                "The request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid learning request",
                "The request is missing a required field or has an invalid value");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidParameters(HandlerMethodValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid learning request",
                "The request is missing a required field or has an invalid value");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
