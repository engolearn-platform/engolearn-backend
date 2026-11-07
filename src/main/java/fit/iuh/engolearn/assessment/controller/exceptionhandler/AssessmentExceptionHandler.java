package fit.iuh.engolearn.assessment.controller.exceptionhandler;
import fit.iuh.engolearn.assessment.controller.AssessmentController;

import fit.iuh.engolearn.assessment.exception.AssessmentException;
import fit.iuh.engolearn.quota.contract.exception.QuotaUnavailableException;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import fit.iuh.engolearn.shared.security.UnauthenticatedException;
import jakarta.servlet.http.*;
import java.net.URI;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=AssessmentController.class)
public class AssessmentExceptionHandler {
    @ExceptionHandler({AssessmentException.class,UnauthenticatedException.class,QuotaUnavailableException.class,SubscriptionUnavailableException.class,
            MethodArgumentNotValidException.class,HandlerMethodValidationException.class,HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class,org.springframework.dao.DataAccessException.class})
    public ProblemDetail handle(Exception e,HttpServletRequest request,HttpServletResponse response) {
        int status=503;String code="UPSTREAM_UNAVAILABLE",detail="Không xác nhận được quyền hoặc hạn mức. Hãy thử lại.";
        if(e instanceof AssessmentException a) {status=a.status();code=a.code();detail=a.getMessage();}
        else if(e instanceof UnauthenticatedException) {status=401;code="AUTHENTICATION_REQUIRED";detail="Vui lòng đăng nhập.";}
        else if(e instanceof org.springframework.dao.DataAccessException) {status=503;code="ASSESSMENT_STORAGE_UNAVAILABLE";detail="Không lưu được bài. Hãy thử lại cùng yêu cầu.";}
        else if(e instanceof MethodArgumentNotValidException || e instanceof HandlerMethodValidationException
                || e instanceof HttpMessageNotReadableException || e instanceof MissingRequestHeaderException || e instanceof MethodArgumentTypeMismatchException) {
            status=400;code="INVALID_REQUEST";detail="Dữ liệu yêu cầu chưa hợp lệ.";
        }
        var p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),detail);
        p.setType(URI.create("about:blank"));p.setInstance(URI.create(request.getRequestURI()));p.setProperty("code",code);
        if(e instanceof MethodArgumentNotValidException v) p.setProperty("fieldErrors",v.getBindingResult().getFieldErrors().stream()
                .map(f->Map.of("field",f.getField(),"message",f.getDefaultMessage()==null?"Invalid":f.getDefaultMessage())).toList());
        response.setHeader("Cache-Control","no-store");return p;
    }
}
