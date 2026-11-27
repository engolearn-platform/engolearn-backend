package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.topic.controller.TopicManagementResourceController;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicMinorRequiresMajorException;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(assignableTypes = TopicManagementResourceController.class)
public class TopicManagementMinorExceptionHandler {
    @ExceptionHandler(TopicMinorRequiresMajorException.class)
    public ProblemDetail major(TopicMinorRequiresMajorException exception) {
        return problem(HttpStatus.BAD_REQUEST, "MINOR_REQUIRES_MAJOR", exception.getMessage());
    }

    @ExceptionHandler(TopicMutationValidationException.class)
    public ProblemDetail invalid(TopicMutationValidationException exception) {
        ProblemDetail response = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
        response.setProperty("fieldErrors", exception.getFieldErrors());
        return response;
    }

    @ExceptionHandler(TopicRevisionConflictException.class)
    public ProblemDetail conflict(TopicRevisionConflictException exception) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({TopicNotFoundException.class, TopicItemNotFoundException.class})
    public ProblemDetail missing(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail response = ProblemDetail.forStatusAndDetail(status, message);
        response.setProperty("code", code);
        response.setProperty("message", message);
        return response;
    }
}
