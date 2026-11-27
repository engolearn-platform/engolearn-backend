package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionReadinessResponse;

public interface QuestionPoolQuestionReadinessService {
    QuestionPoolQuestionReadinessResponse check(String questionId);
}
