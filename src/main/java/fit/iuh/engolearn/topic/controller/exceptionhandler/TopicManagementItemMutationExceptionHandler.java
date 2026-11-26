package fit.iuh.engolearn.topic.controller.exceptionhandler;

import com.mongodb.MongoException;
import fit.iuh.engolearn.topic.controller.TopicManagementItemMutationController;
import fit.iuh.engolearn.topic.controller.TopicMajorWorkingDraftController;
import fit.iuh.engolearn.topic.controller.TopicManagementResourceController;
import fit.iuh.engolearn.topic.exception.TopicItemMutationBlockedException;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicRevisionConflictException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {TopicManagementItemMutationController.class, TopicManagementResourceController.class,
        TopicMajorWorkingDraftController.class})
public class TopicManagementItemMutationExceptionHandler {
    @ExceptionHandler({TopicNotFoundException.class, TopicItemNotFoundException.class})
    public ProblemDetail notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(TopicMutationValidationException.class)
    public ProblemDetail validation(TopicMutationValidationException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
        problem.setProperty("fieldErrors", exception.getFieldErrors());
        return problem;
    }

    @ExceptionHandler(TopicRevisionConflictException.class)
    public ProblemDetail staleRevision(TopicRevisionConflictException exception) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(TopicItemMutationBlockedException.class)
    public ProblemDetail businessRule(TopicItemMutationBlockedException exception) {
        return problem(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler({MongoException.class, DataAccessException.class})
    public ProblemDetail persistenceConflict(RuntimeException exception) {
        if (isWriteConflict(exception)) {
            return problem(HttpStatus.CONFLICT, "CONFLICT", "Topic changed during this save. Reload before retrying.");
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Topic item mutation failed.");
    }

    private static boolean isWriteConflict(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof MongoException mongo
                    && (mongo.getCode() == 112 || mongo.getMessage() != null
                    && mongo.getMessage().contains("WriteConflict"))) return true;
            if (current.getMessage() != null && current.getMessage().contains("TransientTransactionError")) return true;
        }
        return false;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        problem.setProperty("message", message);
        return problem;
    }
}
