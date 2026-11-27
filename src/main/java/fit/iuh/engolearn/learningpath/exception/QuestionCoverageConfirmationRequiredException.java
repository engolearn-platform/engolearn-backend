package fit.iuh.engolearn.learningpath.exception;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolCoverageWarningResponse;
import java.util.List;
import lombok.Getter;

@Getter
public class QuestionCoverageConfirmationRequiredException extends RuntimeException {
    private final List<QuestionPoolCoverageWarningResponse> warnings;

    public QuestionCoverageConfirmationRequiredException(List<QuestionPoolCoverageWarningResponse> warnings) {
        super("Confirm the coverage warning before retiring this question");
        this.warnings = List.copyOf(warnings);
    }
}
