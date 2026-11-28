package fit.iuh.engolearn.learningpath.controller.exceptionhandler;

import fit.iuh.engolearn.learningpath.controller.LearningPathController;
import fit.iuh.engolearn.media.contract.exception.MediaNotFoundException;
import fit.iuh.engolearn.media.contract.exception.MediaStorageUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LearningPathController.class)
public class LearningPathMediaExceptionHandler {
    @ExceptionHandler(MediaNotFoundException.class)
    public ProblemDetail missing(MediaNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Không tìm thấy audio của Learning Path");
    }

    @ExceptionHandler(MediaStorageUnavailableException.class)
    public ProblemDetail unavailable(MediaStorageUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE", "Dịch vụ audio tạm thời không khả dụng");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
