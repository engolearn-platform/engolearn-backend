package fit.iuh.engolearn.placement.controller.exceptionhandler;

import fit.iuh.engolearn.placement.controller.PlacementAudioController;
import fit.iuh.engolearn.placement.exception.PlacementRequestException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = PlacementAudioController.class)
public class PlacementAudioImportExceptionHandler {
    @ExceptionHandler(PlacementRequestException.class)
    public ProblemDetail invalid(PlacementRequestException failure) {
        var problem = ProblemDetail.forStatusAndDetail(failure.status(), failure.getMessage());
        problem.setProperty("code", failure.code());
        return problem;
    }
}
