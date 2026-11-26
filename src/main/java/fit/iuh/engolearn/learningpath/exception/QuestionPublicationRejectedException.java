package fit.iuh.engolearn.learningpath.exception;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionReadinessResponse;
import lombok.Getter;

@Getter
public class QuestionPublicationRejectedException extends RuntimeException {
    private final String code;
    private final QuestionPoolQuestionReadinessResponse readiness;

    public QuestionPublicationRejectedException(String code, String message, QuestionPoolQuestionReadinessResponse readiness) {
        super(message);
        this.code = code;
        this.readiness = readiness;
    }
}
