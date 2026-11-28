package fit.iuh.engolearn.managementdashboard.controller.exceptionhandler;

import fit.iuh.engolearn.managementdashboard.controller.DashboardSummaryController;
import fit.iuh.engolearn.subscription.contract.exception.SubscriptionUnavailableException;
import com.mongodb.MongoException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DashboardSummaryController.class)
public class DashboardSummaryExceptionHandler {
    @ExceptionHandler({DataAccessException.class, MongoException.class, SubscriptionUnavailableException.class})
    public ProblemDetail unavailable(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Dashboard statistics are temporarily unavailable");
        problem.setProperty("code", "DASHBOARD_SOURCE_UNAVAILABLE");
        return problem;
    }
}
