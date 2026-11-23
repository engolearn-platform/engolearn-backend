package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.topic.controller.TopicMediaController;
import fit.iuh.engolearn.topic.exception.TopicMutationValidationException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TopicMediaController.class)
public class TopicMediaExceptionHandler {
    @ExceptionHandler({MediaUploadRejectedException.class, TopicMutationValidationException.class})
    public ProblemDetail rejected(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "MEDIA_UPLOAD_REJECTED", exception.getMessage());
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ProblemDetail missing(MediaNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Topic media was not found.");
    }

    @ExceptionHandler(TopicNotFoundException.class)
    public ProblemDetail topicMissing(TopicNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(MediaStorageUnavailableException.class)
    public ProblemDetail unavailable(MediaStorageUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE", "Media storage is temporarily unavailable.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        problem.setProperty("message", message);
        return problem;
    }
}
