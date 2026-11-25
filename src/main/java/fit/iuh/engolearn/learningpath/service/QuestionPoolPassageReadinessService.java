package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageReadinessResponse;

public interface QuestionPoolPassageReadinessService {
    QuestionPoolPassageReadinessResponse check(String passageId);
}
