package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.request.CreateQuestionPoolPassageRequest;
import fit.iuh.engolearn.learningpath.dto.response.QuestionPoolPassageResponse;
import java.io.InputStream;

public interface QuestionPoolPassageManagementService {
    QuestionPoolPassageResponse create(CreateQuestionPoolPassageRequest request,
            InputStream audio, String filename, String contentType, long sizeBytes);
}
