package fit.iuh.engolearn.topic.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TopicSpeakingProfileException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public TopicSpeakingProfileException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
}
