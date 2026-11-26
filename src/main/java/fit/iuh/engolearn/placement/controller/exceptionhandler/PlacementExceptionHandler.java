package fit.iuh.engolearn.placement.controller.exceptionhandler;

import fit.iuh.engolearn.placement.controller.PlacementAttemptController;
import fit.iuh.engolearn.placement.controller.PlacementResultController;
import fit.iuh.engolearn.placement.controller.PlacementLearningController;
import fit.iuh.engolearn.user.contract.exception.UserLevelPromotionException;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(assignableTypes = {PlacementAttemptController.class, PlacementResultController.class, PlacementLearningController.class})
public class PlacementExceptionHandler {
    // Cross-module failures remain retryable here; User does not expose HTTP semantics in its contract.
    @ExceptionHandler(UserLevelPromotionException.class)
    public ProblemDetail levelUnavailable(UserLevelPromotionException exception, HttpServletRequest request,
                                          HttpServletResponse response) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "PLACEMENT_LEVEL_SYNC_UNAVAILABLE",
                "Could not update your learning level. Please retry.", request, response);
    }

    @ExceptionHandler(PlacementRequestException.class)
    public ProblemDetail placementError(PlacementRequestException exception, HttpServletRequest request,
                                        HttpServletResponse response) {
        return problem(exception.status(), exception.code(), exception.getMessage(), request, response);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class})
    public ProblemDetail invalidRequest(Exception exception, HttpServletRequest request, HttpServletResponse response) {
        return problem(HttpStatus.BAD_REQUEST, "PLACEMENT_INVALID_REQUEST", "Invalid Placement request",
                request, response);
    }

    private ProblemDetail problem(HttpStatusCode status, String code, String detail,
                                  HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
