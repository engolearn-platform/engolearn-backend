package fit.iuh.engolearn.user.controller.exceptionhandler;

import fit.iuh.engolearn.user.controller.UserManagementController;
import fit.iuh.engolearn.user.exception.UserManagementFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=UserManagementController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserManagementExceptionHandler {
    @ExceptionHandler(UserManagementFailure.class)
    public ProblemDetail failure(UserManagementFailure error) {
        var status = switch (error.reason()) {
            case ADMIN_TARGET_READ_ONLY -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case EMAIL_ALREADY_EXISTS, USER_STATE_CONFLICT -> HttpStatus.CONFLICT;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, error.getMessage());
        problem.setProperty("code", error.reason().name());
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalid() { return failure(new UserManagementFailure(UserManagementFailure.Reason.VALIDATION_ERROR)); }

    @ExceptionHandler({TransactionException.class, DataAccessException.class})
    public ProblemDetail unavailable() { return failure(new UserManagementFailure(UserManagementFailure.Reason.SERVICE_UNAVAILABLE)); }

}
