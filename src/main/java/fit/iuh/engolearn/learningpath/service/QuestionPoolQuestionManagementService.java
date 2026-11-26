package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionListResponse;

public interface QuestionPoolQuestionManagementService {
    QuestionPoolQuestionListResponse list(ListQuestionPoolQuestionsRequest request);
    QuestionPoolQuestionDetailResponse detail(String questionId);
}
