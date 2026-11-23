package fit.iuh.engolearn.speaking.controller;

import fit.iuh.engolearn.speaking.exception.SpeakingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SpeakingController.class)
public class SpeakingExceptionHandler {
    @ExceptionHandler(SpeakingException.class)
    public ProblemDetail speaking(SpeakingException error) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(error.status(), error.getMessage());
        detail.setProperty("code", error.code());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid Speaking request");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DuplicateKeyException.class})
    public ProblemDetail conflict(RuntimeException error) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Speaking state changed; reload the session before retrying");
        detail.setProperty("code", "STATE_CHANGED");
        return detail;
    }
}
