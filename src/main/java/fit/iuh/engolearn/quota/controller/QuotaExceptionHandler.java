package fit.iuh.engolearn.quota.controller;

import fit.iuh.engolearn.quota.contract.QuotaUnavailableException;
import fit.iuh.engolearn.shared.security.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-10)
@RestControllerAdvice(assignableTypes = QuotaController.class)
public class QuotaExceptionHandler {
    @ExceptionHandler(QuotaUnavailableException.class)
    public ProblemDetail unavailable(QuotaUnavailableException exception, HttpServletRequest request, HttpServletResponse response) {
        return problem(503, "Quota unavailable", "Quota summary is temporarily unavailable", "QUOTA_SUMMARY_UNAVAILABLE", request, response);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ProblemDetail unauthenticated(UnauthenticatedException exception, HttpServletRequest request, HttpServletResponse response) {
        return problem(401, "Authentication required", "Authentication is required", "AUTHENTICATION_REQUIRED", request, response);
    }

    private ProblemDetail problem(int status, String title, String detail, String code, HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(status), detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
