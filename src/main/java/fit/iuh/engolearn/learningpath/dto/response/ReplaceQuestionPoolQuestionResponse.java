package fit.iuh.engolearn.learningpath.dto.response;

import java.util.List;

public record ReplaceQuestionPoolQuestionResponse(
        QuestionPoolQuestionRetirementResponse retiredQuestion, PublishQuestionPoolQuestionResponse publishedQuestion,
        List<QuestionPoolCoverageResponse> coverage, List<QuestionPoolCoverageWarningResponse> warnings) {
    public ReplaceQuestionPoolQuestionResponse {
        coverage = List.copyOf(coverage);
        warnings = List.copyOf(warnings);
    }
}
