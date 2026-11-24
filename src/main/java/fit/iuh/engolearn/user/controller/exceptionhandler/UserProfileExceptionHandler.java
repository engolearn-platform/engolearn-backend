package fit.iuh.engolearn.user.controller.exceptionhandler;

import fit.iuh.engolearn.user.controller.UserProfileController;
import fit.iuh.engolearn.user.exception.ProfileNotFoundException;
import fit.iuh.engolearn.user.exception.InvalidDisplayNameException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserProfileController.class)
public class UserProfileExceptionHandler {
    @ExceptionHandler(ProfileNotFoundException.class)
    public ProblemDetail handle(ProfileNotFoundException failure) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, failure.getMessage());
        detail.setProperty("code", "PROFILE_NOT_FOUND");
        return detail;
    }

    @ExceptionHandler(InvalidDisplayNameException.class)
    public ProblemDetail handle(InvalidDisplayNameException failure) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
        detail.setProperty("code", "INVALID_DISPLAY_NAME");
        return detail;
    }
}
