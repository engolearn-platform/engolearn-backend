package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageResponse;
import java.io.InputStream;
import fit.iuh.engolearn.learningpath.dto.response.DeleteQuestionPoolPassageResponse;
import fit.iuh.engolearn.learningpath.dto.request.UpdateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageDetailResponse;
import fit.iuh.engolearn.learningpath.dto.request.ListQuestionPoolPassagesRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageListResponse;
import fit.iuh.engolearn.learningpath.dto.request.PublishQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.response.PublishQuestionPoolPassageResponse;

public interface QuestionPoolPassageManagementService {
    PublishQuestionPoolPassageResponse publish(String passageId, PublishQuestionPoolPassageRequest request);

    DeleteQuestionPoolPassageResponse delete(String passageId, int revision);

    QuestionPoolPassageResponse update(String passageId, UpdateQuestionPoolPassageRequest request,
            InputStream audio, String filename, String contentType, long sizeBytes);

    QuestionPoolPassageDetailResponse detail(String passageId);

    QuestionPoolPassageListResponse list(ListQuestionPoolPassagesRequest request);

    QuestionPoolPassageResponse create(CreateQuestionPoolPassageRequest request,
            InputStream audio, String filename, String contentType, long sizeBytes);
}
