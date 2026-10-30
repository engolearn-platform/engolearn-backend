package fit.iuh.engolearn.subscription.controller.exceptionhandler;
import fit.iuh.engolearn.subscription.controller.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=AdminConfigurationController.class)
public class AdminConfigurationExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class,SubscriptionRequestException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.HttpMediaTypeNotSupportedException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ProblemDetail handle(Exception exception,HttpServletRequest request,HttpServletResponse response){
        int status=exception instanceof SubscriptionRequestException e?e.status():400;
        ProblemDetail result=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),exception instanceof SubscriptionRequestException e?e.getMessage():"Request fields or values are invalid");
        result.setType(URI.create("about:blank"));result.setInstance(URI.create(request.getRequestURI()));result.setProperty("code",exception instanceof SubscriptionRequestException e?e.code():"INVALID_REQUEST");
        if(exception instanceof SubscriptionRequestException e&&!e.fieldErrors().isEmpty())result.setProperty("fieldErrors",e.fieldErrors());
        if(exception instanceof MethodArgumentNotValidException validation)
            result.setProperty("fieldErrors", validation.getBindingResult().getFieldErrors().stream()
                .map(error -> new SubscriptionRequestException.FieldError(error.getField().replace(".<list element>", ""), error.getDefaultMessage())).toList());
        response.setHeader("Cache-Control","no-store");return result;
    }
}
