package fit.iuh.engolearn.learningpath.controller.exceptionhandler;

import fit.iuh.engolearn.learningpath.controller.QuestionPoolPassageManagementController;
import fit.iuh.engolearn.learningpath.exception.InvalidPassageException;
import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.learningpath.exception.QuestionPoolPassageNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.learningpath.exception.PassageUpdateConflictException;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = QuestionPoolPassageManagementController.class)
public class QuestionPoolPassageManagementExceptionHandler {
    @ExceptionHandler({InvalidPassageException.class, MediaUploadRejectedException.class})
    public ProblemDetail invalid(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PASSAGE", exception.getMessage());
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class, IOException.class})
    public ProblemDetail malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PASSAGE_REQUEST", "Passage request is missing or invalid");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail unsupported(HttpMediaTypeNotSupportedException exception) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use multipart/form-data with a JSON passage part");
    }

    @ExceptionHandler(PassageUpdateConflictException.class)
    public ProblemDetail updateConflict(PassageUpdateConflictException exception) {
        return problem(HttpStatus.CONFLICT, "PASSAGE_UPDATE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(QuestionPoolPassageNotFoundException.class)
    public ProblemDetail passageMissing(QuestionPoolPassageNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "PASSAGE_NOT_FOUND", "Passage was not found");
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
    public ProblemDetail persistenceUnavailable(DataAccessException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "PASSAGE_STORAGE_UNAVAILABLE", "Passage storage is temporarily unavailable");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
