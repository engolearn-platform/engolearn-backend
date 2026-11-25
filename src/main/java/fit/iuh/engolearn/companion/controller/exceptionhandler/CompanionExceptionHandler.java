package fit.iuh.engolearn.companion.controller.exceptionhandler;

import fit.iuh.engolearn.companion.controller.CompanionConversationController;
import fit.iuh.engolearn.companion.controller.CompanionTurnStreamController;
import fit.iuh.engolearn.companion.exception.CompanionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {CompanionConversationController.class, CompanionTurnStreamController.class})
public class CompanionExceptionHandler {
    @ExceptionHandler({CompanionException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            DataAccessException.class})
    public ProblemDetail handle(Exception failure, HttpServletRequest request, HttpServletResponse response) {
        int status = HttpStatus.SERVICE_UNAVAILABLE.value();
        String code = "COMPANION_STORAGE_UNAVAILABLE";
        String detail = "The companion service is temporarily unavailable. Retry the request.";
        boolean includeRetryable = true;
        boolean retryable = true;

        if (failure instanceof CompanionException companion) {
            status = companion.getStatus();
            code = companion.getCode();
            detail = companion.getSafeDetail();
            retryable = companion.isRetryable();
        } else if (failure instanceof DataAccessException) {
            status = HttpStatus.SERVICE_UNAVAILABLE.value();
        } else {
            status = HttpStatus.BAD_REQUEST.value();
            code = "INVALID_REQUEST";
            detail = "Request fields or values are invalid.";
            includeRetryable = false;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        if (includeRetryable) problem.setProperty("retryable", retryable);
        response.setHeader("Cache-Control", "no-store");
        return problem;
    }
}
