package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.topic.controller.TopicManagementPublishController;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicPublishValidationException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TopicManagementPublishController.class)
public class TopicManagementPublishExceptionHandler {
    @ExceptionHandler(TopicPublishValidationException.class)
    public ProblemDetail invalid(TopicPublishValidationException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "PUBLISH_VALIDATION_FAILED", exception.getMessage());
        problem.setProperty("violations", exception.getViolations());
        return problem;
    }

    @ExceptionHandler(TopicRevisionConflictException.class)
    public ProblemDetail conflict(TopicRevisionConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(TopicNotFoundException.class)
    public ProblemDetail notFound(TopicNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        problem.setProperty("message", message);
        return problem;
    }
}
