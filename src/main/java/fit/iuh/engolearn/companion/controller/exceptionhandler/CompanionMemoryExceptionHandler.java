package fit.iuh.engolearn.companion.controller.exceptionhandler;

import fit.iuh.engolearn.companion.controller.CompanionMemoryController;
import fit.iuh.engolearn.companion.exception.CompanionMemoryException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice(assignableTypes = CompanionMemoryController.class)
public class CompanionMemoryExceptionHandler {
    @ExceptionHandler({CompanionMemoryException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class, DataAccessException.class})
    public ProblemDetail handle(Exception failure, HttpServletRequest request, HttpServletResponse response) {
        int status = HttpStatus.SERVICE_UNAVAILABLE.value();
        String code = "COMPANION_STORAGE_UNAVAILABLE";
        String detail = "Memory storage is temporarily unavailable.";
        if (failure instanceof CompanionMemoryException memory) {
            status = memory.getStatus(); code = memory.getCode(); detail = memory.getSafeDetail();
        } else if (!(failure instanceof DataAccessException)) {
            status = HttpStatus.BAD_REQUEST.value(); code = "INVALID_REQUEST";
            detail = "Request fields or values are invalid.";
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("about:blank")); problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        if (status == 503) problem.setProperty("retryable", true);
        response.setHeader("Cache-Control", "no-store");
        return problem;
    }
}
