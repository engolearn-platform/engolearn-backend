package fit.iuh.engolearn.learningpath.service;

import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathRequest;
import fit.iuh.engolearn.learningpath.dto.request.CreateLearningPathByAssessmentRequest;
import fit.iuh.engolearn.learningpath.dto.response.CreateLearningPathResponse;

public interface LearningPathCreationService {
    CreateLearningPathResponse create(CreateLearningPathRequest request, String userId);
    CreateLearningPathResponse createForAssessment(CreateLearningPathByAssessmentRequest request, String userId);
}
