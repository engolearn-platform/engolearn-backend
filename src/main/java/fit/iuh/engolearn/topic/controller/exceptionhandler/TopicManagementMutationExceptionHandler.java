package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.topic.controller.TopicManagementMutationController;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TopicManagementMutationController.class)
public class TopicManagementMutationExceptionHandler {
    @ExceptionHandler(TopicMutationValidationException.class)
    public ProblemDetail invalid(TopicMutationValidationException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
        problem.setProperty("fieldErrors", exception.getFieldErrors());
        return problem;
    }

    @ExceptionHandler(TopicNotFoundException.class)
    public ProblemDetail notFound(TopicNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(TopicRevisionConflictException.class)
    public ProblemDetail conflict(TopicRevisionConflictException exception) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        problem.setProperty("message", message);
        return problem;
    }
}
