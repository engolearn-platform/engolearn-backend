package fit.iuh.engolearn.subscription.controller.exceptionhandler;
import fit.iuh.engolearn.subscription.controller.*;
import fit.iuh.engolearn.shared.security.UnauthenticatedException;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=SubscriptionController.class)
public class SubscriptionExceptionHandler {
    @ExceptionHandler({SubscriptionRequestException.class,SubscriptionUnavailableException.class,UnauthenticatedException.class,HttpMessageNotReadableException.class,org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ProblemDetail handle(Exception ex,HttpServletRequest request,HttpServletResponse response) {
        int status=503;String code="SUBSCRIPTION_STATE_UNAVAILABLE",detail="Subscription state cannot be confirmed";
        if(ex instanceof SubscriptionRequestException e) {status=e.status();code=e.code();detail=e.getMessage();}
        else if(ex instanceof UnauthenticatedException) {status=401;code="AUTHENTICATION_REQUIRED";detail="Authentication is required";}
        else if(ex instanceof HttpMessageNotReadableException || ex instanceof org.springframework.web.HttpMediaTypeNotSupportedException) {status=400;code="INVALID_REQUEST";detail="A valid empty JSON object is required";}
        response.setHeader("Cache-Control","no-store");ProblemDetail p=ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(status),detail);p.setType(URI.create("about:blank"));p.setInstance(URI.create(request.getRequestURI()));p.setProperty("code",code);return p;
    }
}
