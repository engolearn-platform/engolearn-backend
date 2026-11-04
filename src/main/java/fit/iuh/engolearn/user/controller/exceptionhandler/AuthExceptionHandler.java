package fit.iuh.engolearn.user.controller.exceptionhandler;

import fit.iuh.engolearn.user.controller.AuthController;
import fit.iuh.engolearn.user.service.impl.AuthFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(AuthFailure.class)
    public ProblemDetail handle(AuthFailure failure) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(failure.getStatus()), failure.getMessage());
        detail.setProperty("code", failure.getCode());
        return detail;
    }
}
