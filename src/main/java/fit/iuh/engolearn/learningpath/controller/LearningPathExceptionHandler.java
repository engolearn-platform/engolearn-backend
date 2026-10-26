package fit.iuh.engolearn.learningpath.controller;

import fit.iuh.engolearn.learningpath.exception.RecommendationChangedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = LearningPathController.class)
public class LearningPathExceptionHandler {

    @ExceptionHandler(RecommendationChangedException.class)
    public ProblemDetail handleRecommendationChanged(RecommendationChangedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                exception.getMessage());
        problem.setTitle("Learning Path recommendation changed");
        problem.setProperty("recommendation", exception.getRecommendation());
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.getStatusCode(),
                exception.getReason() == null ? "Learning Path request failed" : exception.getReason()
        );
        problem.setTitle("Learning Path request failed");
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidParameters(HandlerMethodValidationException exception) {
        return invalidRequest();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleInvalidParameterType(MethodArgumentTypeMismatchException exception) {
        return invalidRequest();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
        return invalidRequest();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception) {
        return invalidRequest();
    }

    private ProblemDetail invalidRequest() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "A required Learning Path parameter is missing or invalid"
        );
        problem.setTitle("Invalid Learning Path request");
        return problem;
    }
}
