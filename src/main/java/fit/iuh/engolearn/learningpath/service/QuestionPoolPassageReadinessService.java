package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageReadinessResponse;

public interface QuestionPoolPassageReadinessService {
    QuestionPoolPassageReadinessResponse checkForQuestionPublication(String passageId);
    QuestionPoolPassageReadinessResponse check(String passageId);
}
