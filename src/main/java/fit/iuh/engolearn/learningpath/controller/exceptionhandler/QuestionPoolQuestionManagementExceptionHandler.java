package fit.iuh.engolearn.learningpath.controller.exceptionhandler;

import fit.iuh.engolearn.learningpath.controller.QuestionPoolQuestionManagementController;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolQuestionNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = QuestionPoolQuestionManagementController.class)
public class QuestionPoolQuestionManagementExceptionHandler {
    @ExceptionHandler({BindException.class, HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_QUESTION_REQUEST", "Question request is invalid");
    }

    @ExceptionHandler(QuestionPoolQuestionNotFoundException.class)
    public ProblemDetail missing(QuestionPoolQuestionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question was not found");
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ProblemDetail mediaMissing(MediaNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Passage audio was not found");
    }

    @ExceptionHandler(MediaStorageUnavailableException.class)
    public ProblemDetail mediaUnavailable(MediaStorageUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE", "Media storage is temporarily unavailable");
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail storageUnavailable(DataAccessException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "QUESTION_STORAGE_UNAVAILABLE", "Question or passage storage is temporarily unavailable");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
