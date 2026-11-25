package fit.iuh.engolearn.grammar.controller.exceptionhandler;

import fit.iuh.engolearn.grammar.controller.GrammarAdminController;
import fit.iuh.engolearn.grammar.controller.GrammarLearnerController;
import fit.iuh.engolearn.grammar.exception.GrammarRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
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

@RestControllerAdvice(assignableTypes = {GrammarAdminController.class, GrammarLearnerController.class})
public class GrammarAdminExceptionHandler {
    @ExceptionHandler(GrammarRequestException.class)
    public ProblemDetail rejected(GrammarRequestException exception, HttpServletRequest request,
                                  HttpServletResponse response) {
        ProblemDetail detail = problem(exception.getStatus(), exception.getCode(), exception.getMessage(),
                request, response);
        if (exception.getChecklist() != null) {
            detail.setProperty("checks", exception.getChecklist().checks());
        }
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request,
                                     HttpServletResponse response) {
        ProblemDetail detail = invalidRequest(request, response);
        detail.setProperty("fieldErrors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message",
                        error.getDefaultMessage() == null ? "Không hợp lệ." : error.getDefaultMessage())).toList());
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ProblemDetail invalidRequest(HttpServletRequest request, HttpServletResponse response) {
        return problem(HttpStatus.BAD_REQUEST, "GRAMMAR_INVALID_REQUEST", "Dữ liệu gửi lên không hợp lệ.",
                request, response);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ProblemDetail conflict(HttpServletRequest request, HttpServletResponse response) {
        return problem(HttpStatus.CONFLICT, "GRAMMAR_VERSION_CONFLICT",
                "Nội dung vừa thay đổi. Vui lòng tải lại và kiểm tra lại.", request, response);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ProblemDetail storageUnavailable(HttpServletRequest request, HttpServletResponse response) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "GRAMMAR_STORAGE_UNAVAILABLE",
                "Chưa thể truy cập dữ liệu ngữ pháp. Vui lòng thử lại sau.", request, response);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message, HttpServletRequest request,
                                         HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", code);
        return detail;
    }
}
