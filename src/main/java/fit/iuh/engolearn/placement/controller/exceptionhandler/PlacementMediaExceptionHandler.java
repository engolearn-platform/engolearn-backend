package fit.iuh.engolearn.placement.controller.exceptionhandler;

import fit.iuh.engolearn.media.contract.exception.*;
import fit.iuh.engolearn.placement.controller.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {PlacementAudioController.class, PlacementAttemptController.class,
        PlacementResultController.class, PlacementAdminQuestionController.class, PlacementAdminTestSetController.class,
        PlacementAdminAttemptController.class})
public class PlacementMediaExceptionHandler {
    @ExceptionHandler(MediaNotFoundException.class)
    public ProblemDetail missing(MediaNotFoundException failure) { return problem(HttpStatus.SERVICE_UNAVAILABLE, "PLACEMENT_MEDIA_NOT_FOUND"); }
    @ExceptionHandler(MediaStorageUnavailableException.class)
    public ProblemDetail unavailable(MediaStorageUnavailableException failure) { return problem(HttpStatus.SERVICE_UNAVAILABLE, "PLACEMENT_MEDIA_UNAVAILABLE"); }
    @ExceptionHandler(MediaUploadRejectedException.class)
    public ProblemDetail invalid(MediaUploadRejectedException failure) { return problem(HttpStatus.BAD_REQUEST, "PLACEMENT_AUDIO_INVALID"); }
    private ProblemDetail problem(HttpStatus status, String code) {
        var result = ProblemDetail.forStatusAndDetail(status, status == HttpStatus.BAD_REQUEST
                ? "The audio file is not supported." : "Placement audio is temporarily unavailable.");
        result.setProperty("code", code);
        return result;
    }
}
