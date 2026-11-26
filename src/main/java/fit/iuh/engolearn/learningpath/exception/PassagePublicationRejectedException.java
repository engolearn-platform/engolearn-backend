package fit.iuh.engolearn.learningpath.exception;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageReadinessResponse;
import lombok.Getter;

@Getter
public class PassagePublicationRejectedException extends RuntimeException {
    private final String code;
    private final QuestionPoolPassageReadinessResponse readiness;

    public PassagePublicationRejectedException(String code, String message, QuestionPoolPassageReadinessResponse readiness) {
        super(message);
        this.code = code;
        this.readiness = readiness;
    }
}
