package fit.iuh.engolearn.placement.controller.exceptionhandler;

import fit.iuh.engolearn.placement.controller.PlacementAdminAttemptController;
import fit.iuh.engolearn.placement.controller.PlacementAdminQuestionController;
import fit.iuh.engolearn.placement.controller.PlacementAdminTestSetController;
import fit.iuh.engolearn.placement.exception.PlacementQuestionValidationException;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {PlacementAdminQuestionController.class, PlacementAdminTestSetController.class,
        PlacementAdminAttemptController.class})
public class PlacementAdminExceptionHandler {
    @ExceptionHandler(PlacementRequestException.class)
    public ProblemDetail blocked(PlacementRequestException exception, HttpServletRequest request,
                                 HttpServletResponse response) {
        ProblemDetail problem = problem(exception.status(), exception.code(), exception.getMessage(), request, response);
        if (exception instanceof PlacementQuestionValidationException validation) {
            problem.setProperty("fieldErrors", validation.fields().entrySet().stream()
                    .map(entry -> Map.of("field", entry.getKey(), "message", entry.getValue())).toList());
        }
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ProblemDetail invalidRequest(Exception exception, HttpServletRequest request, HttpServletResponse response) {
        return problem(HttpStatus.BAD_REQUEST, "PLACEMENT_INVALID_REQUEST", "Dữ liệu gửi lên không hợp lệ.", request, response);
    }

    private ProblemDetail problem(HttpStatus status, String code, String message, HttpServletRequest request,
                                  HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
