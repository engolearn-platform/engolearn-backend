package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.topic.controller.TopicManagementReadController;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = TopicManagementReadController.class)
public class TopicManagementReadExceptionHandler {
    @ExceptionHandler({TopicNotFoundException.class, TopicItemNotFoundException.class})
    public ProblemDetail notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalidRequest(MethodArgumentTypeMismatchException exception) {
        String message = "Invalid value for " + exception.getName();
        var response = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        response.setProperty("fieldErrors", List.of(Map.of("field", exception.getName(), "message", message)));
        return response;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail validationFailed(ConstraintViolationException exception) {
        var response = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid management topic query");
        response.setProperty("fieldErrors", exception.getConstraintViolations().stream()
                .map(TopicManagementReadExceptionHandler::fieldError).toList());
        return response;
    }

    private static Map<String, String> fieldError(ConstraintViolation<?> violation) {
        String field = "query";
        for (Path.Node node : violation.getPropertyPath()) field = node.getName();
        return Map.of("field", field, "message", violation.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail response = ProblemDetail.forStatusAndDetail(status, message);
        response.setProperty("code", code);
        response.setProperty("message", message);
        return response;
    }
}
