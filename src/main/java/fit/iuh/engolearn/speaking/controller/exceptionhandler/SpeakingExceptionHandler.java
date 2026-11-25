package fit.iuh.engolearn.speaking.controller.exceptionhandler;

import fit.iuh.engolearn.speaking.controller.SpeakingController;
import fit.iuh.engolearn.speaking.exception.SpeakingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = SpeakingController.class)
public class SpeakingExceptionHandler {
    @ExceptionHandler(SpeakingException.class)
    public ProblemDetail speaking(SpeakingException error) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(error.status(), error.getMessage());
        detail.setProperty("code", error.code());
        detail.setProperty("retryable", error.retryable());
        if (error.activeSessionId() != null) detail.setProperty("activeSessionId", error.activeSessionId());
        return detail;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalid(Exception error) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid Speaking request");
        String code = error instanceof MethodArgumentTypeMismatchException type && "scopeType".equals(type.getName())
                || error instanceof MethodArgumentNotValidException validation && validation.getBindingResult().hasFieldErrors("scope")
                ? "INVALID_SCOPE" : "INVALID_REQUEST";
        detail.setProperty("code", code);
        detail.setProperty("retryable", false);
        return detail;
    }
}
