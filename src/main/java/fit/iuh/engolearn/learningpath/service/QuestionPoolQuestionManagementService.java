package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolQuestionsRequest;
import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.PublishQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.response.PublishQuestionPoolQuestionResponse;
import fit.iuh.engolearn.learningpath.dto.request.RetireQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.request.ReplaceQuestionPoolQuestionRequest;
import fit.iuh.engolearn.learningpath.dto.response.RetireQuestionPoolQuestionResponse;
import fit.iuh.engolearn.learningpath.dto.response.ReplaceQuestionPoolQuestionResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionMutationResponse;
import fit.iuh.engolearn.learningpath.dto.response.DeleteQuestionPoolQuestionResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionDetailResponse;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolQuestionListResponse;

public interface QuestionPoolQuestionManagementService {
    RetireQuestionPoolQuestionResponse retire(String questionId, RetireQuestionPoolQuestionRequest request);
    ReplaceQuestionPoolQuestionResponse replace(String questionId, ReplaceQuestionPoolQuestionRequest request);
    PublishQuestionPoolQuestionResponse publish(String questionId, PublishQuestionPoolQuestionRequest request);
    QuestionPoolQuestionMutationResponse create(CreateQuestionPoolQuestionRequest request);
    QuestionPoolQuestionMutationResponse update(String questionId, UpdateQuestionPoolQuestionRequest request);
    DeleteQuestionPoolQuestionResponse delete(String questionId, int revision);
    QuestionPoolQuestionMutationResponse copy(String questionId, int revision);
    QuestionPoolQuestionListResponse list(ListQuestionPoolQuestionsRequest request);
    QuestionPoolQuestionDetailResponse detail(String questionId);
}
