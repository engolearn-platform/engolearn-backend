package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.topic.controller.TopicBrowseController;
import fit.iuh.engolearn.topic.exception.InvalidBrowseRequestException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TopicBrowseController.class)
public class TopicBrowseExceptionHandler {

    /** A topic that does not exist, was never published, or whose release no longer resolves. */
    @ExceptionHandler(TopicNotFoundException.class)
    public ProblemDetail handleTopicNotFound(TopicNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Topic not found", exception.getMessage());
    }

    /** An unusable level, sort order, page size or cursor. */
    @ExceptionHandler(InvalidBrowseRequestException.class)
    public ProblemDetail handleInvalidBrowseRequest(InvalidBrowseRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid browse request", exception.getMessage());
    }

    /** A query value that cannot be converted to the expected type, for example {@code limit=abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String detail = "Parameter '" + exception.getName() + "' has an unusable value: " + exception.getValue();
        return problem(HttpStatus.BAD_REQUEST, "Invalid browse request", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
