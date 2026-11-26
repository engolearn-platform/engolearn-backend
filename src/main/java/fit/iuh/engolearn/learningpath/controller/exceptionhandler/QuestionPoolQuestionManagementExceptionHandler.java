package fit.iuh.engolearn.learningpath.controller.exceptionhandler;

import fit.iuh.engolearn.learningpath.controller.QuestionPoolQuestionManagementController;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolQuestionNotFoundException;
import fit.iuh.engolearn.learningpath.exception.InvalidQuestionException;
import fit.iuh.engolearn.learningpath.exception.QuestionMutationConflictException;
import fit.iuh.engolearn.learningpath.exception.QuestionWriteUnavailableException;
import fit.iuh.engolearn.learningpath.exception.QuestionPublicationRejectedException;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = QuestionPoolQuestionManagementController.class)
public class QuestionPoolQuestionManagementExceptionHandler {
    @ExceptionHandler(QuestionPublicationRejectedException.class)
    public ProblemDetail publicationRejected(QuestionPublicationRejectedException exception) {
        var problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, exception.getCode(), exception.getMessage());
        problem.setProperty("readiness", exception.getReadiness());
        return problem;
    }
    @ExceptionHandler({BindException.class, HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ProblemDetail malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_QUESTION_REQUEST", "Question request is invalid");
    }

    @ExceptionHandler(InvalidQuestionException.class)
    public ProblemDetail invalid(InvalidQuestionException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_QUESTION", exception.getMessage());
    }

    @ExceptionHandler(QuestionMutationConflictException.class)
    public ProblemDetail conflict(QuestionMutationConflictException exception) {
        return problem(HttpStatus.CONFLICT, "QUESTION_WRITE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(QuestionWriteUnavailableException.class)
    public ProblemDetail writeUnavailable(QuestionWriteUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "QUESTION_STORAGE_UNAVAILABLE", "Question storage or transactions are temporarily unavailable");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail unsupported(HttpMediaTypeNotSupportedException exception) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use application/json");
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
