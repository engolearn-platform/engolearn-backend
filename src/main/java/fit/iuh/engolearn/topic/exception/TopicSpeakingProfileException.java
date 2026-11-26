package fit.iuh.engolearn.topic.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import fit.iuh.engolearn.topic.dto.response.TopicPublishReadinessResponse.Violation;
import java.util.List;

@Getter
public class TopicSpeakingProfileException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<Violation> violations;

    public TopicSpeakingProfileException(HttpStatus status, String code, String detail) {
        this(status, code, detail, List.of());
    }

    public TopicSpeakingProfileException(HttpStatus status, String code, String detail, List<Violation> violations) {
        super(detail);
        this.status = status;
        this.code = code;
        this.violations = List.copyOf(violations);
    }
}
