package fit.iuh.engolearn.topic.controller.exceptionhandler;

import fit.iuh.engolearn.topic.controller.TopicSpeakingProfileController;
import fit.iuh.engolearn.topic.exception.TopicItemNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicNotFoundException;
import fit.iuh.engolearn.topic.exception.TopicSpeakingProfileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TopicSpeakingProfileController.class)
public class TopicSpeakingProfileExceptionHandler {
    @ExceptionHandler(TopicSpeakingProfileException.class)
    public ProblemDetail profile(TopicSpeakingProfileException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setProperty("code", exception.getCode());
        detail.setProperty("violations", exception.getViolations());
        return detail;
    }

    @ExceptionHandler({TopicNotFoundException.class, TopicItemNotFoundException.class})
    public ProblemDetail missing(RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setProperty("code", "INVALID_SCOPE");
        return detail;
    }
}
