package fit.iuh.engolearn.media.infra.web.exceptionhandler;

import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import fit.iuh.engolearn.media.contract.exception.MediaUploadRejectedException;
import fit.iuh.engolearn.media.infra.web.MediaSmokeTestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MediaSmokeTestController.class)
public class MediaSmokeTestExceptionHandler {

    @ExceptionHandler(MediaUploadRejectedException.class)
    public ProblemDetail handleUploadRejected(MediaUploadRejectedException exception) {
        return problem(HttpStatus.BAD_REQUEST, "MEDIA_UPLOAD_REJECTED", exception.getMessage());
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ProblemDetail handleMediaNotFound(MediaNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "The requested media does not exist");
    }

    @ExceptionHandler(MediaStorageUnavailableException.class)
    public ProblemDetail handleStorageUnavailable(MediaStorageUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE",
                "Media storage is temporarily unavailable");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
