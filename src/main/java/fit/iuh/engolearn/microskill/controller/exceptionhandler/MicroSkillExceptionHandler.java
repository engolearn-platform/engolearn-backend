package fit.iuh.engolearn.microskill.controller.exceptionhandler;
import fit.iuh.engolearn.microskill.controller.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {MicroSkillController.class, MicroSkillManagementController.class})
public class MicroSkillExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail status(ResponseStatusException failure) {
        return ProblemDetail.forStatusAndDetail(failure.getStatusCode(), failure.getReason() == null ? "Catalog operation rejected" : failure.getReason());
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail duplicate(DuplicateKeyException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Micro-skill code or canonical name already exists");
    }
}
