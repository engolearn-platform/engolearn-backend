package fit.iuh.engolearn.subscription.controller.exceptionhandler;

import fit.iuh.engolearn.subscription.controller.AdminSubscriptionProductController;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminSubscriptionProductController.class)
public class AdminSubscriptionProductExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, SubscriptionRequestException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ProblemDetail handle(Exception exception, HttpServletRequest request, HttpServletResponse response) {
        int status = exception instanceof SubscriptionRequestException e ? e.status() : 400;
        String detail = exception instanceof SubscriptionRequestException e
                ? e.getMessage() : "Request fields or values are invalid";
        String code = exception instanceof SubscriptionRequestException e ? e.code() : "INVALID_REQUEST";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        if (exception instanceof SubscriptionRequestException e && !e.fieldErrors().isEmpty())
            problem.setProperty("fieldErrors", e.fieldErrors());
        if (exception instanceof MethodArgumentNotValidException validation)
            problem.setProperty("fieldErrors", validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> new SubscriptionRequestException.FieldError(
                            error.getField().replace(".<list element>", ""), error.getDefaultMessage())).toList());
        response.setHeader("Cache-Control", "no-store");
        return problem;
    }
}
